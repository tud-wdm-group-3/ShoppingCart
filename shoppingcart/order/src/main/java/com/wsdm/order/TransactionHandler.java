package com.wsdm.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.*;

@Service
public class TransactionHandler {

    private Map<Integer, Order> currentOrders = new HashMap<>();
    private Map<Integer, Map<String, Object>> stockChecks = new HashMap<>();

    @Autowired
    private KafkaTemplate<Integer, Object> kafkaTemplate;

    public boolean startCheckout(Order order) {
        sendStockCheck(order);
        return true; // TODO
    }


    /**
     * The functions below are in chronological order of processing for one order.
     */

    public void sendStockCheck(Order order) {
        System.out.println("sending stock check");

        // STEP 1: SEND STOCK CHECK
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);

        Map<String, Object> log = new HashMap<>();
        log.put("total", stockPartition.size());
        log.put("count", 0);
        log.put("flag", true);
        stockChecks.put(order.getOrderId(), log);

        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            kafkaTemplate.send("toStockCheck", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }


    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockCheck",
                    partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0")}))
    public void getStockCheckResponse(List<Integer> stockResponse) {
        System.out.println("got stock response");

        Map<String, Object> log = stockChecks.get(stockResponse.get(0));
        if ((int) log.get("flag") != 1 || !processResponse(stockResponse)){
            // TODO: failed
        } else {
            log.put("count", (int) log.get("count") + 1);
            log.put("flag", stockResponse.get(1) == 1);
            stockChecks.put(stockResponse.get(0), log);

            if (log.get("count") == log.get("total") && (boolean) log.get("flag")){
                // Remove it from the log since check is completed
                stockChecks.remove(stockResponse.get(0));
                sendPaymentTransaction(currentOrders.get(stockChecks.get(0)));
            }
        }
    }

    private void sendPaymentTransaction(Order order) {
        System.out.println("sending payment check");

        // STEP 3: START PAYMENT TRANSACTION
        int userId = order.getUserId();
        int partition = getPartition(userId, Environment.numPaymentInstances);
        List<Integer> data = Arrays.asList(userId, order.getTotalCost());

        kafkaTemplate.send("toPaymentTransaction", partition, order.getOrderId(), data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0")}))
    private void getPaymentResponse(List<Integer> paymentResponse) {
        System.out.println("get payment response");

        // STEP 4: RECEIVE RESPONSE FROM PAYMENT TRANSACTION
        if (processResponse(paymentResponse)) {
            sendStockTransaction(currentOrders.get(paymentResponse.get(0)));
        } else {
            // TODO: failed
        }
    }

    private void sendStockTransaction(Order order) {
        System.out.println("sending stock transaction");

        // STEP 5: START STOCK TRANSACTION
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);
        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            kafkaTemplate.send("toStockTransaction", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0")}))
    private void getStockTransactionResponse(List<Integer> response) {
        System.out.println("received stock transaction response");

        // STEP 6: RECEIVE RESPONSE FROM STOCK TRANSACTION
        if (processResponse(response)) {
            // TODO: now we are done
        } else {
            // TODO: failed
        }
    }


    /** Helper functions */

    private boolean processResponse(List<Integer> response) {
        int orderId = response.get(0);
        int result = response.get(1);

        if (!currentOrders.containsKey(orderId)) {
            throw new IllegalArgumentException("Received response to order not in transaction.");
        }

        return result == 1;
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
