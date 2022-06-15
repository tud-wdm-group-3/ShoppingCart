package com.wsdm.stock;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StockService {

    private final StockRepository stockRepository;

    @Value("${PARTITION_ID}")
    private int myStockInstanceId;

    private int numStockInstances = 2;

    private int numPaymentInstances = 2;

    private int numOrderInstances = 2;

    @Autowired
    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public int createItem(int price) {
        Stock stock = new Stock(price);
        stock.setPrice(price);

        // Convert local to global id
        stockRepository.save(stock);
        int globalId = stock.getLocalId() * numOrderInstances + myStockInstanceId;
        stock.setItemId(globalId);
        stockRepository.save(stock);

        // Update item logs in order instances
        Map<String, Integer> data = Map.of("itemId", globalId, "price", stock.getPrice());
        for (int partition = 0; partition < numOrderInstances; partition++) {
            fromStockTemplate.send("fromStockItemPrice", partition, data);
        }

        return globalId;
    }

    public Optional<Stock> findItem(int itemId) {
        int localId = (itemId - myStockInstanceId) / numStockInstances;
        return stockRepository.findById(localId);
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
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getStockCheck(ConsumerRecord<Integer, List<Integer>> request) {
        System.out.println("Received stock check " + request);
        int orderId = request.key();
        int partition = orderId % numOrderInstances;

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems(request.value());
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
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getStockTransaction(ConsumerRecord<Integer, List<Integer>> request) {
        System.out.println("Received stock transaction " + request);
        int orderId = request.key();
        int orderPartition = orderId % numOrderInstances;

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems(request.value());
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());

        // subtract amounts from stock
        int curId = -1;
        for (int id : ids) {
            int required = itemIdToAmount.get(id);
            // For rollbacks in case individual item no longer available
            if (!subtractStock(id, required)) {
                curId = id;
                break;
            }
        }

        // Internal rollback
        if (curId != -1){
            for (int id : ids){
                if (id == curId) break;
                int required = itemIdToAmount.get(id);
                addStock(id, required);
            }
        }

        boolean enoughInStock = curId == -1;
        Map<String, Object> data = Map.of("orderId", orderId, "stockId", myStockInstanceId, "enoughInStock", enoughInStock);
        fromStockTemplate.send("fromStockTransaction", orderPartition, orderId, data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toStockRollback",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getStockRollback(ConsumerRecord<Integer, List<Integer>> request) {
        System.out.println("Received stock rollback " + request);

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems(request.value());
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());

        // Rollback amounts to stock
        for (int id : ids) {
            int returned = itemIdToAmount.get(id);
            addStock(id, returned);
        }
    }

    private Map<Integer, Integer> countItems(List<Integer> items) {
        Map<Integer, Integer> itemIdToAmount = new HashMap<>();
        for (int itemId : items) {
            itemIdToAmount.put(itemId, itemIdToAmount.getOrDefault(itemId, 0) + 1);
        }
        return itemIdToAmount;
    }
}
