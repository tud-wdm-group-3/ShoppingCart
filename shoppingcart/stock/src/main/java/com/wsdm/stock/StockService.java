package com.wsdm.stock;

import com.wsdm.stock.utils.NameUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(isolation = Isolation.SERIALIZABLE)
public class StockService {

    final StockRepository stockRepository;

    @Value("${PARTITION}")
    private int myStockInstanceId;

    private String myReplicaId;

    private int numStockInstances = 2;

    private int numPaymentInstances = 2;

    private int numOrderInstances = 2;

    @Autowired
    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;

        myReplicaId = NameUtils.getHostname();
        // Let kafka be able to get the hostname
        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext();
        try {
            standardEvaluationContext.registerFunction("getHostname", NameUtils.class.getDeclaredMethod("getHostname", null));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Stock service started");
    }

    public int createItem(int price) {
        Stock stock = new Stock(price);

        // Convert local to global id
        stockRepository.save(stock);
        int globalId = stock.getLocalId() * numOrderInstances + myStockInstanceId;

        // Update item logs in order instances
        Map<String, Integer> data = Map.of("itemId", globalId, "price", stock.getPrice());
        fromStockTemplate.send("fromStockItemPrice", 0, data);

        stock.setItemId(globalId);
        stock.setStockBroadcasted(Stock.StockBroadcasted.YES);
        stockRepository.save(stock);

        return globalId;
    }

    public Optional<Stock> findItem(int itemId) {
        int localId = (itemId - myStockInstanceId) / numStockInstances;
        Optional<Stock> optStock = stockRepository.findById(localId);
        if (optStock.isPresent()) {
            Stock order = optStock.get();
            if (order.getStockBroadcasted() == Stock.StockBroadcasted.NO) {
                // do not return unbroadcasted orders
                optStock = Optional.empty();
            }
        }
        return optStock;
    }

    public List<Stock> findItems(List<Integer> itemIds) {
        for (int i = 0; i < itemIds.size(); i++) {
            int itemId = itemIds.get(i);
            int localId = (itemId - myStockInstanceId) / numStockInstances;
            itemIds.set(i, localId);
        }
        List<Stock> res = stockRepository.findAllById(itemIds);
        return res;
    }

    public boolean addStock(int itemId, int amount) {
        Optional<Stock> res = findItem(itemId);
        if(res.isPresent()) {
            Stock stock = res.get();
            stock.setAmount(stock.getAmount()+amount);
            stockRepository.save(stock);
            return true;
        }
        return false;
    }

    public boolean subtractStock(int itemId, int amount) {
        Optional<Stock> res = findItem(itemId);
        if(res.isPresent()) {
            Stock stock = res.get();
            if (stock.getAmount() >= amount) {
                stock.setAmount(stock.getAmount()-amount);
                stockRepository.save(stock);
                return true;
            }
        }
        return false;
    }

    /**
     * Internal messaging
     */

    @Autowired
    private KafkaTemplate<Integer, Object> fromStockTemplate;

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toStockCheck",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getStockCheck(Map<String, Object> request) {
        System.out.println("Received stock check " + request);
        int orderId = (int) request.get("orderId");
        int partition = orderId % numOrderInstances;

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems((List<Integer>) request.get("items"));
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findItems(ids);

        boolean enoughInStock = true;
        if (stocks.size() == ids.size()){
            for (Stock stock : stocks){
                int required = itemIdToAmount.get(stock.getItemId());
                enoughInStock = enoughInStock && stock.getAmount() >= required;
                if (!enoughInStock) {
                    break;
                }
            }
        } else {
            throw new IllegalStateException("An item id in the order does not exist in stock");
        }

        Map<String, Object> data = Map.of("orderId", orderId, "enoughInStock", enoughInStock);
        fromStockTemplate.send("fromStockCheck", partition, orderId, data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getStockTransaction(Map<String, Object> request) {
        System.out.println("Received stock transaction " + request);
        int orderId = (int) request.get("orderId");
        int orderPartition = orderId % numOrderInstances;

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems((List<Integer>) request.get("items"));
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findItems(ids);

        boolean enoughInStock = true;
        for (Stock stock : stocks) {
            enoughInStock = enoughInStock && stock.getAmount() >= itemIdToAmount.get(stock.getItemId());
            assert(!stock.getOrderToItemsProcessed().containsKey(orderId));
        }

        if (enoughInStock) {
            for (Stock stock : stocks) {
                int amount = itemIdToAmount.get(stock.getItemId());
                stock.setAmount(stock.getAmount() - amount);
                Map<Integer, Integer> orderToItemsProcessed = stock.getOrderToItemsProcessed();
                orderToItemsProcessed.put(orderId, amount);
            }
            stockRepository.saveAll(stocks);
        }

        Map<String, Object> data = Map.of("orderId", orderId, "stockId", myStockInstanceId, "enoughInStock", enoughInStock);
        fromStockTemplate.send("fromStockTransaction", orderPartition, orderId, data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toStockRollback",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getStockRollback(Map<String, Object> request) {
        System.out.println("Received stock rollback " + request);
        int orderId = (int) request.get("orderId");

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems((List<Integer>) request.get("items"));
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findItems(ids);

        // Rollback amounts to stock
        for (Stock stock : stocks) {
            int amount = itemIdToAmount.get(stock.getItemId());
            Map<Integer, Integer> orderToItemsProcessed = stock.getOrderToItemsProcessed();
            if (orderToItemsProcessed.containsKey(orderId)) {
                assert(amount == orderToItemsProcessed.get(orderId));
                orderToItemsProcessed.remove(orderId);
                stock.setAmount(stock.getAmount() + amount);
            }
        }
        stockRepository.saveAll(stocks);
    }

    private Map<Integer, Integer> countItems(List<Integer> items) {
        Map<Integer, Integer> itemIdToAmount = new HashMap<>();
        for (int itemId : items) {
            itemIdToAmount.put(itemId, itemIdToAmount.getOrDefault(itemId, 0) + 1);
        }
        return itemIdToAmount;
    }
}
