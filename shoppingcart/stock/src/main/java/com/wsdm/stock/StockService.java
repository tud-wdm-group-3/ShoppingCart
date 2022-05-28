package com.wsdm.stock;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StockService {

    private final StockRepository stockRepository;

    @Autowired
    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Optional<Stock> findStock(int item_id) {
        Optional<Stock> res = stockRepository.findById(item_id);
        return res;
    }

    public boolean addStock(int item_id, int amount) {
        Optional<Stock> res = stockRepository.findById(item_id);
        if(res.isPresent()) {
            Stock stock = res.get();
            stock.setAmount(stock.getAmount()+amount);
            stockRepository.save(stock);
            return true;
        }
        return false;
    }

    public boolean subtractStock(int item_id, int amount) {
        Optional<Stock> res = stockRepository.findById(item_id);
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
        stockRepository.save(stock);

        return stock;
    }

    /**
     * Internal messaging
     */

    private final int numStockInstances = 1;
    private final int numPaymentInstances = 1;
    private final int numOrderInstances = 1;
    private final int myStockInstanceId = 1;

    @Autowired
    private KafkaTemplate<Integer, Boolean> fromStockTemplate;

    @Transactional
    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = this.myStockInstanceId, initialOffset = "0")}))
    protected void getStockCheck(ConsumerRecord<Integer, List<Integer>> request) {
        int orderId = request.key();
        Map<Integer, Integer> itemIdToAmount = new HashMap<>();
        for (int itemId : request.value()) {
            itemIdToAmount.put(itemId, itemIdToAmount.getOrDefault(itemId, 0) + 1);
        }

        for (int itemId : itemIdToAmount.keySet()) {
            
        }
        int partition = orderId % numOrderInstances;
        fromStockTemplate.send("fromStockCheck", partition, orderId, result);
    }

    // TODO: Stock transaction
}
