package com.wsdm.stock;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StockService {

    private final StockRepository stockRepository;

    @Autowired
    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Optional<Stock> findStock(int itemId) {
        return stockRepository.findById(itemId);
    }

    public List<Stock> findStocks(List<Integer> item_ids) {
        List<Stock> res = stockRepository.findAllById(item_ids);
        return res;
    }

    public boolean addStock(int itemId, int amount) {
        Optional<Stock> res = stockRepository.findById(itemId);
        if(res.isPresent()) {
            Stock stock = res.get();
            stock.setAmount(stock.getAmount()+amount);
            stockRepository.save(stock);
            return true;
        }
        return false;
    }

    public boolean subtractStock(int itemId, int amount) {
        Optional<Stock> res = stockRepository.findById(itemId);
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

    public Stock addItem(double price) {
        Stock stock = new Stock(price);
        stock.setPrice(price);

        // Convert local to global id
        stockRepository.save(stock);
        int globalId = stock.getItemId() * Environment.numOrderInstances + Environment.myStockInstanceId;
        stock.setItemId(globalId);
        stockRepository.save(stock);

        return stock;
    }

    /**
     * Internal messaging
     */

    @Autowired
    private KafkaTemplate<Integer, Boolean> fromStockTemplate;

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toStockCheck",
            partitionOffsets = {@PartitionOffset(partition = "${myStockInstanceId}", initialOffset = "0")}))
    protected void getStockCheck(ConsumerRecord<Integer, List<Integer>> request) {
        int orderId = request.key();
        int partition = orderId % Environment.numOrderInstances;

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems(request.value());
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findStocks(ids);

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

        fromStockTemplate.send("fromStockCheck", partition, orderId, enoughInStock);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${myStockInstanceId}", initialOffset = "0")}))
    protected void getStockTransaction(ConsumerRecord<Integer, List<Integer>> request) {
        int orderId = request.key();
        int partition = orderId % Environment.numOrderInstances;

        // Count items
        Map<Integer, Integer> itemIdToAmount = countItems(request.value());
        List<Integer> ids = new ArrayList<>(itemIdToAmount.keySet());
        List<Stock> stocks = findStocks(ids);

        // Check if still enough of everything
        // this prevents having to do rollbacks internally
        // No reason to check if an item id is wrong at this point
        boolean enoughInStock = true;
        for (Stock stock : stocks){
            int required = itemIdToAmount.get(stock.getItemId());
            enoughInStock = enoughInStock && stock.getAmount() >= required;
            if (!enoughInStock) {
                break;
            }
        }

        // subtract amounts from stock
        if (enoughInStock) {
            for (Stock stock : stocks) {
                int amountInStock = stock.getAmount();
                int required = itemIdToAmount.get(stock.getItemId());
                stock.setAmount(amountInStock - required);
            }
        }

        fromStockTemplate.send("fromStockTransaction", partition, orderId, enoughInStock);
    }

    private Map<Integer, Integer> countItems(List<Integer> items) {
        Map<Integer, Integer> itemIdToAmount = new HashMap<>();
        for (int itemId : items) {
            itemIdToAmount.put(itemId, itemIdToAmount.getOrDefault(itemId, 0) + 1);
        }
        return itemIdToAmount;
    }
}
