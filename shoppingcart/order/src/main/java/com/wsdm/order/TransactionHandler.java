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

    // TODO: fix these to the actual values
    private final int numStockInstances = 1;
    private final int numPaymentInstances = 1;
    private final int numOrderInstances = 1;
    private final int myOrderInstanceId = 1;

    private Map<Integer, Order> currentOrders;

    public TransactionHandler() {
        this.currentOrders = new HashMap<>();
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
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), numStockInstances);
        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            toStockTemplate.send("toStockCheck", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockCheck",
                    partitionOffsets = {@PartitionOffset(partition = this.myOrderInstanceId, initialOffset = "0")}))
    public void getStockCheckResponse(Pair<Integer, Boolean> orderResultPair) {
        // STEP 2: RECEIVE RESPONSE FROM STOCK CHECK
        if (processResponse(orderResultPair)) {
            sendPaymentTransaction(currentOrders.get(orderResultPair.getFirst()));
        } else {
            // TODO: failed
        }
    }

    @Autowired
    private KafkaTemplate<Integer, Pair<Integer, Integer>> toPaymentTemplate;

    private void sendPaymentTransaction(Order order) {
        // STEP 3: START PAYMENT TRANSACTION
        int orderId = order.getOrderId();
        int userId = order.getUserId();
        int totalCost = order.getTotalCost();
        int partition = getPartition(userId, numPaymentInstances);
        Pair<Integer, Integer> userAndPrice = Pair.of(userId, totalCost);

        toPaymentTemplate.send("toPaymentTransaction", partition, orderId, userAndPrice);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = this.myOrderInstanceId, initialOffset = "0")}))
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
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), numStockInstances);
        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
                toStockTemplate.send("toStockTransaction", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = this.myOrderInstanceId, initialOffset = "0")}))
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
        boolean isPossible = orderResultPair.getSecond();

        if (!currentOrders.containsKey(orderId)) {
            throw new IllegalArgumentException("Received response to order not in transaction.");
        }

        return isPossible;
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
