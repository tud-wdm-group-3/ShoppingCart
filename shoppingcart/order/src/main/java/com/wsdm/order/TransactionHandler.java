package com.wsdm.order;

import com.wsdm.order.utils.NameUtils;
import com.wsdm.order.utils.Partitioner;
import com.wsdm.order.utils.Template;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.net.InetAddress;
import java.util.*;

@Service
public class TransactionHandler {

    private static Environment env;

    private String myReplicaId = NameUtils.getHostname();
    public String getMyReplicaId() {
        return myReplicaId;
    }

    /**
     * Maps orderId to deferredResult.
     */
    private static Map<Integer, DeferredResult<ResponseEntity>> pendingResponses = new HashMap<>();

    /**
     * Map orderId to order for caching purposes.
     */
    private static Map<Integer, Order> currentCheckoutOrders = new HashMap<>();

    /**
     * Map orderId to properties map, including total, count, flag etc.
     */
    private static Map<Integer, Map> stockCheckLog = new HashMap<>();

    /**
     * Map orderId to properties map, including total, count, and
     * a special map (confirmations) containing which partitions confirmed the transaction.
     */
    private static Map<Integer, Map> transactionLog = new HashMap<>();

    private static OrderRepository orderRepository;

    public TransactionHandler(OrderRepository repository, Environment environment) {
        orderRepository = repository;
        // System.out.println("environment2" + environment);
        env = environment;

        // Do the rollbacks for the failed orders.
        List<Order> failedOrders = orderRepository.findOrdersByInCheckoutAndReplicaHandlingCheckout(true, myReplicaId);
        for (Order order: failedOrders) {
            sendPaymentRollback(order);
            Map<Integer, List<Integer>> stockPartitions = Partitioner.getPartition(order.getItems(), Partitioner.Service.STOCK, env);
            Map<Integer, Boolean> fakeConfirmations = new HashMap<>();
            for (int stockPartition : stockPartitions.keySet()) {
                fakeConfirmations.put(stockPartition, true);
            }
            sendStockRollback(order, fakeConfirmations);
            order.setInCheckout(false);
            order.setReplicaHandlingCheckout("");
        }
        orderRepository.saveAll(failedOrders);
    }

    public void startCheckout(Order order, DeferredResult<ResponseEntity> response) {
        order.setInCheckout(true);
        order.setReplicaHandlingCheckout(myReplicaId);
        orderRepository.save(order);
        pendingResponses.put(order.getOrderId(env), response);
        currentCheckoutOrders.put(order.getOrderId(env), order);
        sendStockCheck(order);
    }


    /**
     * The functions below are in chronological order of processing for one order.
     */

    public void sendStockCheck(Order order) {
        // System.out.println("sending stock check for order " + order);

        // STEP 1: SEND STOCK CHECK
        Map<Integer, List<Integer>> stockPartition = Partitioner.getPartition(order.getItems(), Partitioner.Service.STOCK, env);

        Map<String, Object> log = new HashMap<>();
        log.put("total", stockPartition.size());
        log.put("count", 0);
        log.put("flag", true);
        stockCheckLog.put(order.getOrderId(env), log);

        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            // System.out.println("sending stock " + partitionEntry);
            Map<String, Object> data = Map.of("orderId", order.getOrderId(env), "items", partitionEntry.getValue());
            Template.send("toStockCheck", partitionEntry.getKey(), order.getOrderId(env), data);
        }
    }


    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "fromStockCheck",
                    partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    public void getStockCheckResponse(Map<String, Object> stockResponse) {
        int orderId = (int) stockResponse.get("orderId");
        if (currentCheckoutOrders.containsKey(orderId) && stockCheckLog.containsKey(orderId)) {


            boolean enoughInStock = (boolean) stockResponse.get("enoughInStock");

            // Get current order
            Order curOrder = currentCheckoutOrders.get(orderId);

            // Get current order stock check log
            Map<String, Object> curOrderLog = stockCheckLog.get(orderId);

            boolean prevEnoughInStock = (boolean) curOrderLog.get("flag");

            if (prevEnoughInStock && !enoughInStock) {
                // First fail, so imm. send transactionFailed
                transactionFailed(orderId, "not enough in stock.");
            }

            // Update info
            curOrderLog.put("count", (int) curOrderLog.get("count") + 1);
            curOrderLog.put("flag", prevEnoughInStock && enoughInStock);
            stockCheckLog.put(orderId, curOrderLog);

            if (curOrderLog.get("count") == curOrderLog.get("total")) {
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
    }

    private void sendPaymentTransaction(Order order) {
        // System.out.println("sending payment check for order" + order);

        // STEP 3: START PAYMENT TRANSACTION
        int orderId = order.getOrderId(env);
        int userId = order.getUserId();
        int partition = Partitioner.getPartition(userId, Partitioner.Service.PAYMENT, env);
        Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "totalCost", order.getTotalCost());

        Template.send("toPaymentTransaction", partition, order.getOrderId(env), data);
    }

    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "fromPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    private void getPaymentResponse(Map<String, Object> paymentResponse) {
        int orderId = (int) paymentResponse.get("orderId");

        if (currentCheckoutOrders.containsKey(orderId)) {
            // System.out.println("get payment response " + paymentResponse);

            boolean enoughCredit = (boolean) paymentResponse.get("enoughCredit");

            // STEP 4: RECEIVE RESPONSE FROM PAYMENT TRANSACTION
            if (enoughCredit) {
                currentCheckoutOrders.get(orderId).setPaid(true);
                sendStockTransaction(currentCheckoutOrders.get(orderId));
            } else {
                transactionFailed(orderId, "not enough credit.");
            }
        }
    }

    private void sendStockTransaction(Order order) {
        // System.out.println("sending stock transaction for order" + order);

        // STEP 5: START STOCK TRANSACTION
        Map<Integer, List<Integer>> stockPartition = Partitioner.getPartition(order.getItems(), Partitioner.Service.STOCK, env);
        Map<String, Object> log = new HashMap<>();
        for (int partitionId : stockPartition.keySet()) {
            log.put(Integer.toString(partitionId), true);
        }
        log.put("total", stockPartition.size());
        log.put("count", 0);
        log.put("confirmations", new HashMap<Integer, Boolean>());
        transactionLog.put(order.getOrderId(env), log);

        for (Map.Entry<Integer, List<Integer>> partitionEntry : stockPartition.entrySet()) {
            Map<String, Object> data = Map.of("orderId", order.getOrderId(env), "items", partitionEntry.getValue());
            Template.send("toStockTransaction", partitionEntry.getKey(), order.getOrderId(env), data);
        }
    }

    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "fromStockTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    private void getStockTransactionResponse(Map<String, Object> stockResponse) {
        int orderId = (int) stockResponse.get("orderId");
        if (currentCheckoutOrders.containsKey(orderId) && transactionLog.containsKey(orderId)) {
            // System.out.println("received stock transaction response " + stockResponse);


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
                    transactionFailed(orderId, " not enough stock after transaction");
                } else {
                    transactionSucceeded(orderId);
                }
                // Remove order from transaction Log
                transactionLog.remove(orderId);
            }
        }
    }

    private void sendStockRollback(Order order, Map<Integer, Boolean> confirmations) {
        // System.out.println("Sending stock rollback for order " + order + " confirmations " + confirmations);
        Map<Integer, List<Integer>> stockPartition = Partitioner.getPartition(order.getItems(), Partitioner.Service.STOCK, env);
        for (int stockId : confirmations.keySet()) {
            if (confirmations.get(stockId)) {
                // This stock id returned true, so we must rollback
                List<Integer> partitionItems = stockPartition.get(stockId);
                Map<String, Object> data = Map.of("orderId", order.getOrderId(env), "items", partitionItems);
                Template.send("toStockRollback", stockId, order.getOrderId(env), data);
            }
        }
    }

    private void sendPaymentRollback(Order order) {
        // System.out.println("Sending payment rollback for order" + order);
        int userId = order.getUserId();
        int paymentPartition = Partitioner.getPartition(userId, Partitioner.Service.PAYMENT, env);
        Map<String, Object> data = Map.of("orderId", order.getOrderId(env), "userId", userId, "refund", order.getTotalCost());
        Template.send("toPaymentRollback", paymentPartition, order.getOrderId(env), data);
    }

    private void transactionFailed(int orderId, String reason) {
        // System.out.println("transaction for order " + orderId + " failed because " + reason);
        Order order = currentCheckoutOrders.remove(orderId);
        order.setInCheckout(false);
        order.setPaid(false);
        order.setReplicaHandlingCheckout("");
        order.setCheckedOut(false);
        orderRepository.save(order);
        pendingResponses.remove(orderId).setResult(ResponseEntity.status(409).build());
    }

    private void transactionSucceeded(int orderId) {
        // System.out.println("transaction for order " + orderId + " succeeded.");
        Order order = currentCheckoutOrders.remove(orderId);
        order.setInCheckout(false);
        order.setPaid(true);
        order.setReplicaHandlingCheckout("");
        order.setCheckedOut(true);
        orderRepository.save(order);
        pendingResponses.remove(orderId).setResult(ResponseEntity.ok().build());
    }



}
