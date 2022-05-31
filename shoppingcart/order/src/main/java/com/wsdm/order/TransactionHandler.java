package com.wsdm.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionHandler {

    private Map<Integer, Order> currentOrders;
    private Map<Integer, Map<String, Object>> stockChecks;
    private Map<Integer, Map<String, Object>> stockChecks;

    public TransactionHandler() {
        this.currentOrders = new HashMap<>();
        this.stockChecks = new HashMap<>();
    }

    public boolean startCheckout(Order order) {
        sendStockCheck(order);
        return true; // TODO
    }


    /**
     * The functions below are in chronological order of processing for one order.
     */

    @Autowired
    private KafkaTemplate<Integer, List<Integer>> toStockTemplate;

    public void sendStockCheck(Order order) {
        // STEP 1: SEND STOCK CHECK
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);

        Map<String, Object> log = new HashMap<>();
        log.put("total", stockPartition.size());
        log.put("count", 0);
        log.put("flag", true);
        stockChecks.put(order.getOrderId(), log);

        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            toStockTemplate.send("toStockCheck", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }


    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockCheck",
            partitionOffsets = {@PartitionOffset(partition = "${myOrderInstanceId}", initialOffset = "0")}))
    public void getStockCheckResponse(Pair<Integer, Boolean> orderResultPair) {
        // STEP 2: RECEIVE RESPONSE FROM STOCK CHECK
        Map<String, Object> log = stockChecks.get(orderResultPair.getFirst());
        if (!(boolean) log.get("flag") || !processResponse(orderResultPair)){
            // TODO: failed
        } else {
            log.put("count", (int) log.get("count") + 1);
            log.put("flag", orderResultPair.getSecond());
            stockChecks.put(orderResultPair.getFirst(), log);

            if (log.get("count") == log.get("total") && (boolean) log.get("flag")){
                // Remove it from the log since check is completed
                stockChecks.remove(orderResultPair.getFirst());
                sendPaymentTransaction(currentOrders.get(orderResultPair.getFirst()));
            }
        }
    }

    @Autowired
    private KafkaTemplate<Integer, Pair<Integer, Integer>> toPaymentTemplate;

    private void sendPaymentTransaction(Order order) {
        // STEP 3: START PAYMENT TRANSACTION
        int orderId = order.getOrderId();
        int userId = order.getUserId();
        int totalCost = order.getTotalCost();
        int partition = getPartition(userId, Environment.numPaymentInstances);
        Pair<Integer, Integer> userAndPrice = Pair.of(userId, totalCost);

        toPaymentTemplate.send("toPaymentTransaction", partition, orderId, userAndPrice);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${myOrderInstanceId}", initialOffset = "0")}))
    private void getPaymentResponse(Pair<Integer, Boolean> orderResultPair) {
        // STEP 4: RECEIVE RESPONSE FROM PAYMENT TRANSACTION
        if (processResponse(orderResultPair)) {
            sendStockTransaction(currentOrders.get(orderResultPair.getFirst()));
        } else {
            // TODO: failed
        }
    }

    private void sendStockTransaction(Order order) {
        // STEP 5: START STOCK TRANSACTION
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);
        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
                toStockTemplate.send("toStockTransaction", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "#{myOrderInstanceId}", initialOffset = "0")}))
    private void getStockTransactionResponse(Pair<Integer, Boolean> orderResultPair) {
        // STEP 6: RECEIVE RESPONSE FROM STOCK TRANSACTION
        if (processResponse(orderResultPair)) {
            // TODO: now we are done
        } else {
            // TODO: failed
        }
    }


    /** Helper functions */

    private boolean processResponse(Pair<Integer, Boolean> orderResultPair) {
        int orderId = orderResultPair.getFirst();
        boolean result = orderResultPair.getSecond();

        if (!currentOrders.containsKey(orderId)) {
            throw new IllegalArgumentException("Received response to order not in transaction.");
        }

        return result;
    }

    private int getPartition(int id, int numInstances) {
        return id % numInstances;
    }

    private Map<Integer, List<Integer>> getPartition(List<Integer> ids, int numInstances) {
        Map<Integer, List<Integer>> partitionToIds = new HashMap<>();
        for (int id : ids) {
            int partition = getPartition(id, numInstances);
            List<Integer> curPartition = partitionToIds.getOrDefault(partition, new ArrayList<>());
            curPartition.add(id);
            partitionToIds.put(partition, curPartition);
        }
        return partitionToIds;
    }
}
