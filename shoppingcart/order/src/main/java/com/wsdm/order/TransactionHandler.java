package com.wsdm.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;

@Service
public class TransactionHandler {

    /**
     * Maps orderId to deferredResult.
     */
    private Map<Integer, DeferredResult<ResponseEntity>> pendingResponses = new HashMap<>();

    /**
     * Map orderId to order for caching purposes.
     */
    private Map<Integer, Order> currentOrders = new HashMap<>();

    /**
     * Map orderId to properties map, including total, count, flag etc.
     */
    private Map<Integer, Map<String, Object>> stockCheckLog = new HashMap<>();

    /**
     * Map orderId to properties map, including total, count, and
     * a special map (confirmations) containing which partitions confirmed the transaction.
     */
    private Map<Integer, Map<String, Object>> transactionLog = new HashMap<>();

    /**
     * Map itemdId to price.
     */
    private Map<Integer, Integer> itemPrices = new HashMap<>();

    @Autowired
    private KafkaTemplate<Integer, Object> kafkaTemplate;

    final private OrderRepository orderRepository;

    public TransactionHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void startCheckout(Order order, DeferredResult<ResponseEntity> response) {
        pendingResponses.put(order.getOrderId(), response);
        sendStockCheck(order);
    }


    /**
     * The functions below are in chronological order of processing for one order.
     */

    public void sendStockCheck(Order order) {
        System.out.println("sending stock check");

        // STEP 1: SEND STOCK CHECK
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);

        currentOrders.put(order.getOrderId(), order);
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
                    partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    public void getStockCheckResponse(Map<String, Object> stockResponse) {
        System.out.println("got stock response");
        System.out.println(stockResponse);

        int orderId = (int) stockResponse.get("orderId");
        boolean enoughInStock = (boolean) stockResponse.get("enoughInStock");

        // Get current order stock check log
        Map<String, Object> curOrderLog = new HashMap<>(stockCheckLog.get(orderId));

        // Get current order
        Order curOrder = currentOrders.get(orderId);

        boolean prevEnoughInStock = (boolean) curOrderLog.get("flag");

        if (prevEnoughInStock && !enoughInStock) {
            // First fail, so imm. send transactionFailed
            transactionFailed(orderId);
        }

        // Update info
        curOrderLog.put("count", (int) curOrderLog.get("count") + 1);
        curOrderLog.put("flag", prevEnoughInStock && enoughInStock);

        if (curOrderLog.get("count") == curOrderLog.get("total")){
            // Remove it from the log since check is completed
            stockCheckLog.remove(orderId);
            boolean enoughInAllStock = prevEnoughInStock && enoughInStock;
            if (enoughInAllStock && !curOrder.isPaid()) {
                sendPaymentTransaction(currentOrders.get(orderId));
            } else if (enoughInAllStock){
                sendStockTransaction(curOrder);
            }
        }
    }

    private void sendPaymentTransaction(Order order) {
        System.out.println("sending payment check");

        // STEP 3: START PAYMENT TRANSACTION
        int userId = order.getUserId();
        int partition = getPartition(userId, Environment.numPaymentInstances);
        Map<String, Object> data = Map.of("userId", userId, "totalCost", order.getTotalCost());

        kafkaTemplate.send("toPaymentTransaction", partition, order.getOrderId(), data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    private void getPaymentResponse(Map<String, Object> paymentResponse) {
        System.out.println("get payment response");
        int orderId = (int) paymentResponse.get("orderId");
        boolean enoughCredit = (boolean) paymentResponse.get("enoughCredit");

        // STEP 4: RECEIVE RESPONSE FROM PAYMENT TRANSACTION
        if (enoughCredit) {
            currentOrders.get(orderId).setPaid(true);
            sendStockTransaction(currentOrders.get(orderId));
        } else {
            transactionFailed(orderId);
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
        log.put("confirmations", new HashMap<Integer, Boolean>());
        transactionLog.put(order.getOrderId(), log);
        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            kafkaTemplate.send("toStockTransaction", partitionEntry.getKey(), order.getOrderId(), partitionEntry.getValue());
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentUpdate",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    private void getPaymentUpdate(Integer orderId) {
        int localOrderId = (orderId - Environment.myOrderInstanceId) / Environment.numOrderInstances;
        Optional<Order> optOrder = orderRepository.findById(localOrderId);
        if (optOrder.isEmpty()) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist");
        }
        Order order = optOrder.get();
        order.setPaid(true);
        orderRepository.save(order);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    private void getStockTransactionResponse(Map<String, Object> stockResponse) {
        System.out.println("received stock transaction response");

        int orderId = (int) stockResponse.get("orderId");
        int stockId = (int) stockResponse.get("stockId");
        boolean enoughInStock = (boolean) stockResponse.get("enoughInStock");

        // Get logs for this orderId
        Map<String, Object> curOrderLog = transactionLog.get(orderId);
        Map<Integer, Boolean> curOrderConfirmations = (HashMap<Integer, Boolean>) (curOrderLog.get("confirmations"));

        // Process response
        curOrderLog.put("count", (int) curOrderLog.get("count") + 1);
        curOrderConfirmations.put(stockId, enoughInStock);

        // TODO: put back stuff in transactionlog? idk bro

        if (curOrderLog.get("count") == curOrderLog.get("total")){

            if (curOrderConfirmations.values().contains(false)) {
                Order order = currentOrders.get(orderId);
                sendStockRollback(order, curOrderConfirmations);
                sendPaymentRollback(order);
                transactionFailed(orderId);
            } else {
                transactionSucceeded(orderId);
            }
            // Remove order from transaction Log
            transactionLog.remove(orderId);
        }
    }


    /**
     * Used to initialize cache of itemIds, so false relativeToCurrent.
     */
    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockItemPrice",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "false")}))
    private void getItemPrice(Map<String, Integer> item) {
        int itemId = item.get("itemId");
        int price = item.get("price");

        itemPrices.put(itemId, price);
    }

    public void sendOrderExists(int orderId, int method) {
        Optional<Order> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist");
        }
        Order order = optOrder.get();

        int userId = order.getUserId();
        int partition = getPartition(userId, Environment.numPaymentInstances);

        Map<String, Integer> reqValue = Map.of("userId", userId, "method", method, "totalCost", order.getTotalCost());
        kafkaTemplate.send("toPaymentOrderExists", partition, order.getOrderId(), reqValue);
    }

    private void sendStockRollback(Order order, Map<Integer, Boolean> confirmations) {
        Map<Integer, List<Integer>> stockPartition = getPartition(order.getItems(), Environment.numStockInstances);
        for (int stockId : confirmations.keySet()) {
            if (confirmations.get(stockId)) {
                // This stock id returned true, so we must rollback
                List<Integer> partitionItems = stockPartition.get(stockPartition);
                kafkaTemplate.send("toStockRollback", stockId, order.getOrderId(), partitionItems);
            }
        }
    }

    private void sendPaymentRollback(Order order) {
        int userId = order.getUserId();
        int paymentPartition = getPartition(userId, Environment.numPaymentInstances);
        Map<String, Object> data = Map.of("userId", userId, "totalCost", order.getTotalCost());
        kafkaTemplate.send("toPaymentRollback", paymentPartition, order.getOrderId(), data);
    }

    private void transactionFailed(int orderId) {
        currentOrders.remove(orderId);
        pendingResponses.remove(orderId).setResult(ResponseEntity.status(409).build());
    }

    private void transactionSucceeded(int orderId) {
        Order order = currentOrders.remove(orderId);
        sendOrderExists(orderId, 1);
        order.setPaid(true);
        orderRepository.save(order);
        pendingResponses.get(orderId).setResult(ResponseEntity.ok().build());
    }

    /** Helper functions */

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

    public boolean itemExists(int itemId) {
        return itemPrices.containsKey(itemId);
    }

    public int getItemPrice(int itemId) {
        return itemPrices.get(itemId);
    }

}
