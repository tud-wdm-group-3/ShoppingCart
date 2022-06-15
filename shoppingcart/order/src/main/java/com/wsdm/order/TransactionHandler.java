package com.wsdm.order;

import com.wsdm.order.utils.Partitioner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${PARTITION_ID}")
    private int myOrderInstanceId;

    private int numStockInstances = 2;

    private int numPaymentInstances = 2;

    private int numOrderInstances = 2;

    /**
     * Maps orderId to deferredResult.
     */
    private Map<Integer, DeferredResult<ResponseEntity>> pendingResponses = new HashMap<>();

    /**
     * Map orderId to order for caching purposes.
     */
    private Map<Integer, Order> currentCheckoutOrders = new HashMap<>();

    /**
     * Map orderId to properties map, including total, count, flag etc.
     */
    private Map<Integer, Map> stockCheckLog;

    /**
     * Map orderId to properties map, including total, count, and
     * a special map (confirmations) containing which partitions confirmed the transaction.
     */
    private Map<Integer, Map> transactionLog;

    @Autowired
    private KafkaTemplate<Integer, Object> kafkaTemplate;

    final private OrderRepository orderRepository;

    public TransactionHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;

        this.stockCheckLog = new HashMap<>();
        this.transactionLog = new HashMap<>();
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
        Map<Integer, List<Integer>> stockPartition = Partitioner.getPartition(order.getItems(), numStockInstances);

        currentCheckoutOrders.put(order.getOrderId(), order);
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
        System.out.println("got stock response " + stockResponse);

        int orderId = (int) stockResponse.get("orderId");
        boolean enoughInStock = (boolean) stockResponse.get("enoughInStock");

        // Get current order stock check log
        Map<String, Object> curOrderLog = new HashMap<>(stockCheckLog.get(orderId));

        // Get current order
        Order curOrder = currentCheckoutOrders.get(orderId);

        boolean prevEnoughInStock = (boolean) curOrderLog.get("flag");

        if (prevEnoughInStock && !enoughInStock) {
            // First fail, so imm. send transactionFailed
            transactionFailed(orderId);
        }

        // Update info
        curOrderLog.put("count", (int) curOrderLog.get("count") + 1);
        curOrderLog.put("flag", prevEnoughInStock && enoughInStock);
        stockCheckLog.put(orderId, curOrderLog);

        if (curOrderLog.get("count") == curOrderLog.get("total")){
            // Remove it from the log since check is completed
            stockCheckLog.remove(orderId);
            boolean enoughInAllStock = prevEnoughInStock && enoughInStock;
            if (enoughInAllStock && !curOrder.isPaid()) {
                sendPaymentTransaction(currentCheckoutOrders.get(orderId));
            } else if (enoughInAllStock){
                sendStockTransaction(curOrder);
            }
        }
    }

    private void sendPaymentTransaction(Order order) {
        System.out.println("sending payment check for order" + order);

        // STEP 3: START PAYMENT TRANSACTION
        int orderId = order.getOrderId();
        int userId = order.getUserId();
        int partition = Partitioner.getPartition(userId, numPaymentInstances);
        Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "totalCost", order.getTotalCost());

        kafkaTemplate.send("toPaymentTransaction", partition, order.getOrderId(), data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    private void getPaymentResponse(Map<String, Object> paymentResponse) {
        System.out.println("get payment response " + paymentResponse);
        int orderId = (int) paymentResponse.get("orderId");
        boolean enoughCredit = (boolean) paymentResponse.get("enoughCredit");

        // STEP 4: RECEIVE RESPONSE FROM PAYMENT TRANSACTION
        if (enoughCredit) {
            currentCheckoutOrders.get(orderId).setPaid(true);
            sendStockTransaction(currentCheckoutOrders.get(orderId));
        } else {
            transactionFailed(orderId);
        }
    }

    private void sendStockTransaction(Order order) {
        System.out.println("sending stock transaction for order" + order);

        // STEP 5: START STOCK TRANSACTION
        Map<Integer, List<Integer>> stockPartition = Partitioner.getPartition(order.getItems(), numStockInstances);
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

    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    private void getStockTransactionResponse(Map<String, Object> stockResponse) {
        System.out.println("received stock transaction response " + stockResponse);

        int orderId = (int) stockResponse.get("orderId");
        int stockId = (int) stockResponse.get("stockId");
        boolean enoughInStock = (boolean) stockResponse.get("enoughInStock");

        // Get logs for this orderId
        Map<String, Object> curOrderLog = transactionLog.get(orderId);
        Map<Integer, Boolean> curOrderConfirmations = (HashMap<Integer, Boolean>) (curOrderLog.get("confirmations"));

        // Process response
        curOrderLog.put("count", (int) curOrderLog.get("count") + 1);
        curOrderConfirmations.put(stockId, enoughInStock);
        transactionLog.put(orderId, curOrderLog);

        if (curOrderLog.get("count") == curOrderLog.get("total")){

            if (curOrderConfirmations.values().contains(false)) {
                Order order = currentCheckoutOrders.get(orderId);
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

    public void sendOrderExists(Order order, int method) {
        int userId = order.getUserId();
        int partition = Partitioner.getPartition(userId, numPaymentInstances);

        Map<String, Integer> reqValue = Map.of("userId", userId, "method", method, "totalCost", order.getTotalCost());
        kafkaTemplate.send("toPaymentOrderExists", partition, order.getOrderId(), reqValue);
    }

    private void sendStockRollback(Order order, Map<Integer, Boolean> confirmations) {
        System.out.println("Sending stock rollback for order " + order + " confirmations " + confirmations);
        Map<Integer, List<Integer>> stockPartition = Partitioner.getPartition(order.getItems(), numStockInstances);
        for (int stockId : confirmations.keySet()) {
            if (confirmations.get(stockId)) {
                // This stock id returned true, so we must rollback
                List<Integer> partitionItems = stockPartition.get(stockPartition);
                kafkaTemplate.send("toStockRollback", stockId, order.getOrderId(), partitionItems);
            }
        }
    }

    private void sendPaymentRollback(Order order) {
        System.out.println("Sending payment rollback for order" + order);
        int userId = order.getUserId();
        int paymentPartition = Partitioner.getPartition(userId, numPaymentInstances);
        Map<String, Object> data = Map.of("userId", userId, "totalCost", order.getTotalCost());
        kafkaTemplate.send("toPaymentRollback", paymentPartition, order.getOrderId(), data);
    }

    private void transactionFailed(int orderId) {
        System.out.println("transaction for order " + orderId + " failed.");
        currentCheckoutOrders.remove(orderId);
        pendingResponses.remove(orderId).setResult(ResponseEntity.status(409).build());
    }

    private void transactionSucceeded(int orderId) {
        System.out.println("transaction for order " + orderId + " succeeded.");
        Order order = currentCheckoutOrders.remove(orderId);
        sendOrderExists(order, 1);
        order.setPaid(true);
        orderRepository.save(order);
        pendingResponses.remove(orderId).setResult(ResponseEntity.ok().build());
    }
}
