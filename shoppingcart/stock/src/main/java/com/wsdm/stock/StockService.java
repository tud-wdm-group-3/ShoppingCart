package com.wsdm.stock;

import com.wsdm.stock.utils.NameUtils;
import com.wsdm.stock.utils.Partitioner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
public class StockService {

    final StockRepository stockRepository;

    private String myReplicaId = NameUtils.getHostname();
    public String getMyReplicaId() {
        return myReplicaId;
    }

    @Autowired
    Environment env;

    @Autowired
    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;

        // System.out.println("Stock service started with replica-id " + myReplicaId);
        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            Map<String, Object> data = Map.of("itemId", stock.getItemId(env), "price", stock.getPrice());
            fromStockTemplate.send("fromStockItemPrice", 0, data);
        }
    }

    public List<Stock> dump() {
        return stockRepository.findAll();
    }

    public int createItem(double price) {
        Stock stock = new Stock(price);

        // Convert local to global id
        stockRepository.save(stock);
        int globalId = stock.getItemId(env);

        // Update item logs in order instances
        Map<String, Object> data = Map.of("itemId", globalId, "price", stock.getPrice());
        fromStockTemplate.send("fromStockItemPrice", 0, data);

        return globalId;
    }

    public Optional<Stock> findItem(int itemId) {
        Optional<Stock> optStock = stockRepository.findById(Stock.getLocalId(itemId, env));
        return optStock;
    }

    public List<Stock> findItems(List<Integer> itemIds) {
        for (int i = 0; i < itemIds.size(); i++) {
            int itemId = itemIds.get(i);
            int localId = Stock.getLocalId(itemId, env);
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

    
    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toStockCheck",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getStockCheck(Map<String, Object> request) {
        // System.out.println("Received stock check " + request);
        int orderId = (int) request.get("orderId");
        int partition = Partitioner.getPartition(orderId, Partitioner.Service.ORDER, env);

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems((List<Integer>) request.get("items"));
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findItems(ids);

        boolean enoughInStock = true;
        if (stocks.size() == ids.size()){
            for (Stock stock : stocks){
                int required = itemIdToAmount.get(stock.getItemId(env));
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

    
    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getStockTransaction(Map<String, Object> request) {
        // System.out.println("Received stock transaction " + request);
        int orderId = (int) request.get("orderId");
        int orderPartition = Partitioner.getPartition(orderId, Partitioner.Service.ORDER, env);

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems((List<Integer>) request.get("items"));
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findItems(ids);

        boolean enoughInStock = true;
        for (Stock stock : stocks) {
            enoughInStock = enoughInStock && stock.getAmount() >= itemIdToAmount.get(stock.getItemId(env));
            assert(!stock.getOrderToItemsProcessed().containsKey(orderId));
        }

        if (enoughInStock) {
            for (Stock stock : stocks) {
                int amount = itemIdToAmount.get(stock.getItemId(env));
                stock.setAmount(stock.getAmount() - amount);
                Map<Integer, Integer> orderToItemsProcessed = stock.getOrderToItemsProcessed();
                orderToItemsProcessed.put(orderId, amount);
            }
            stockRepository.saveAll(stocks);
        }

        Map<String, Object> data = Map.of("orderId", orderId, "stockId", Partitioner.getPartition(ids.get(0), Partitioner.Service.STOCK, env), "enoughInStock", enoughInStock);
        fromStockTemplate.send("fromStockTransaction", orderPartition, orderId, data);
    }

    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toStockRollback",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getStockRollback(Map<String, Object> request) {
        // System.out.println("Received stock rollback " + request);
        int orderId = (int) request.get("orderId");

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems((List<Integer>) request.get("items"));
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findItems(ids);

        // Rollback amounts to stock
        for (Stock stock : stocks) {
            int amount = itemIdToAmount.get(stock.getItemId(env));
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
