package com.wsdm.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TransactionHandler {

    private Map<Integer, Order> currentOrders = new HashMap<>();
    private Map<Integer, Map<String, Object>> stockCheckLog = new HashMap<>();
    private Map<Integer, Map<String, Object>> transactionLog = new HashMap<>();

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
        stockCheckLog.put(order.getOrderId(), log);

        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            kafkaTemplate.send("toStockCheck", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }


    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockCheck",
                    partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0")}))
    public void getStockCheckResponse(Pair<Integer, Boolean> stockResponse) {
        System.out.println("got stock response");

        Map<String, Object> log = new HashMap<>(stockCheckLog.get(stockResponse.getFirst()));
        if (!(boolean) log.get("flag") || !processResponse(stockResponse)){
            // TODO: failed
        } else {
            log.put("count", (int) log.get("count") + 1);
            log.put("flag", stockResponse.getSecond());
            stockCheckLog.put(stockResponse.getFirst(), log);

            if (log.get("count") == log.get("total") && (boolean) log.get("flag")){
                // Remove it from the log since check is completed
                stockCheckLog.remove(stockResponse.getFirst());
                sendPaymentTransaction(currentOrders.get(stockResponse.getFirst()));
            }
        }
    }

    private void sendPaymentTransaction(Order order) {
        System.out.println("sending payment check");

        // STEP 3: START PAYMENT TRANSACTION
        int userId = order.getUserId();
        int partition = getPartition(userId, Environment.numPaymentInstances);
        Pair<Integer, Integer> data = Pair.of(userId, order.getTotalCost());

        kafkaTemplate.send("toPaymentTransaction", partition, order.getOrderId(), data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0")}))
    private void getPaymentResponse(Pair<Integer, Boolean> paymentResponse) {
        System.out.println("get payment response");

        // STEP 4: RECEIVE RESPONSE FROM PAYMENT TRANSACTION
        if (processResponse(paymentResponse)) {
            sendStockTransaction(currentOrders.get(paymentResponse.getFirst()));
        } else {
            // TODO: failed
        }
    }

    private void sendStockTransaction(Order order) {
        System.out.println("sending stock transaction");

        // STEP 5: START STOCK TRANSACTION
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);
        Map<String, Object> log = new HashMap<>();
        for (int partitionId : stockPartition.keySet()) {
            log.put(Integer.toString(partitionId), true);
        }
        log.put("total", stockPartition.size());
        log.put("count", 0);
        transactionLog.put(order.getOrderId(), log);
        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            kafkaTemplate.send("toStockTransaction", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0")}))
    private void getStockTransactionResponse(Pair<Integer, AbstractMap.SimpleEntry<Integer, Boolean>> response) {
        System.out.println("received stock transaction response");
        Map<String, Object> log = new HashMap<>(transactionLog.get(response.getFirst()));

        log.put("count", (int) log.get("count") + 1);
        log.put(Integer.toString(response.getSecond().getKey()), response.getSecond().getValue());
        transactionLog.put(response.getFirst(), log);

        if (log.get("count") == log.get("total")){

            if (log.values().contains(false)) {
                // Remove count and total (left with partition ids)
                log.remove("count");
                log.remove("total");
                for (String partitionId : log.keySet()) {
                    int stockPartition = Integer.parseInt(partitionId);
                    if ((boolean) log.get(partitionId)) {
                        Order order = currentOrders.get(response.getFirst());
                        List<Integer> instanceItems = getPartition(order.getItems(), Environment.numStockInstances).get(stockPartition);
                        kafkaTemplate.send("toStockRollback", stockPartition, response.getFirst(), instanceItems);
                    }
                }
                Order order = currentOrders.get(response.getFirst());
                int userId = order.getUserId();
                int paymentPartition = getPartition(userId, Environment.numPaymentInstances);
                Pair<Integer, Integer> data = Pair.of(userId, order.getTotalCost());
                kafkaTemplate.send("toPaymentRollback", paymentPartition, response.getFirst(), data);
            } else {
                // TODO: now we are done
            }
            // Remove order from Transaction Log
            transactionLog.remove(response.getFirst());
        }
    }


    /** Helper functions */

    private boolean processResponse(Pair<Integer, Boolean> response) {
        int orderId = response.getFirst();
        boolean result = response.getSecond();

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
