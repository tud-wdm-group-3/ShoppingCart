package com.wsdm.order;

import com.wsdm.order.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;


import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Future;


@Service
public class OrderService {


    private String myReplicaId = NameUtils.getHostname();
    public String getMyReplicaId() {
        return myReplicaId;
    }

    private static OrderRepository repository;

    private TransactionHandler transactionHandler;

    private KafkaTemplate<Integer, Object> kafkaTemplate;

    Environment env;

    @Autowired
    public OrderService(OrderRepository repository, KafkaTemplate<Integer, Object> kafkaTemplate, Environment environment) {
        this.repository = repository;
        // System.out.println("Order service started with replica-id " + myReplicaId);

        Template.addTemplate(kafkaTemplate);
        env = environment;
        transactionHandler = new TransactionHandler(repository, env);

        List<Order> orders = repository.findAll();
        for (Order order : orders) {
            sendOrderExists(order, "create");
        }
    }

    public List<Order> dump() {
        return this.repository.findAll();
    }

    public int createOrder(int userId){
        Order order=new Order(userId);
        repository.save(order);
        sendOrderExists(order, "create");

        return order.getOrderId(env);
    }

    
    public boolean deleteOrder(int orderId){
        Optional<Order> optOrder = findOrder(orderId);
        if (optOrder.isPresent()) {
            Order order = optOrder.get();
            if (mayChangeOrder(order)) {
                sendOrderExists(order, "delete");
                repository.delete(order);
                return true;
            }
        }
        return false;
    }

    public Optional<Order> findOrder(int orderId) {
        // Convert to local id
        Optional<Order> optOrder = repository.findById(Order.getLocalId(orderId, env));
        return optOrder;
    }

    
    public boolean addItemToOrder(int orderId, int itemId){
        // Check if itemId exists
        if (!ItemPrices.itemExists(itemId)) return false;

        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            Set<Integer> items = order.getItems();
            if (mayChangeOrder(order) && !items.contains(itemId)) {
                items.add(itemId);

                // Increase order's total cost
                double price = ItemPrices.getItemPrice(itemId);
                order.setTotalCost(order.getTotalCost() + price);
                repository.save(order);
                return true;
            }
        }
        return false;
    }

    
    public boolean removeItemFromOrder(int orderId,int itemId){
        // Check if itemId exists
        if (!ItemPrices.itemExists(itemId)) return false;

        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            if (mayChangeOrder(order)) {
                Set<Integer> items = order.getItems();
                if (items.contains(itemId)) {
                    items.remove(itemId);

                    // Decrease order's total cost
                    double price = ItemPrices.getItemPrice(itemId);
                    order.setTotalCost(order.getTotalCost() - price);
                    order.setItems(items);

                    repository.save(order);
                    return true;
                }
            }
        }
        return false;
    }

    
    public void checkout(int orderId, DeferredResult<ResponseEntity> response){
        Optional<Order> optOrder = findOrder(orderId);
        if (!optOrder.isPresent()) {
            response.setResult(ResponseEntity.badRequest().build());
            return;
        }
        Order order = optOrder.get();

        if (mayCheckout(order)) {
            transactionHandler.startCheckout(order, response);
        } else {
            response.setResult(ResponseEntity.badRequest().build());
            return;
        }
    }


    /**
     * Item cost and payment functions below. Needed here because need functionality of service
     */


    /**
     * Used to initialize cache of itemIds, so false relativeToCurrent, and partition 0.
     */
    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "fromStockItemPrice",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "false")}))
    private void receiveItemPrice(Map<String, Object> item) {
        int itemId = (int) item.get("itemId");
        double price = (double) item.get("price");
        // System.out.println("Received item cache " + itemId + " with price " + price);

        ItemPrices.addItemPrice(itemId, price);
    }

    /**
     * Payment made or cancelled.
     * By putting them in the same topic, we have a total ordering between pay and cancel,
     * so we always process in the correct order.
     */
    
    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "fromPaymentPaid",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    public void paymentChanged(Map<String, Object> request) {
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        double amount = (double) request.get("amount");
        String type = (String) request.get("type");
        String replicaId = (String) request.get("replicaId");
        int paymentKey = (int) request.get("paymentKey");

        // System.out.println("Received payment made/cancelled with order " + orderId + " from user " + userId + " and amount " + amount);

        Optional<Order> optOrder = findOrder(orderId);

        if (!optOrder.isPresent()) {
            throw new AssertionError("Payment made for non-existent order");
        }

        Order order = optOrder.get();

        // Check if other replicas have processed this already
        if (order.getProcessedPaymentKeys().contains(paymentKey)) {
            return;
        }

        if (type.contains("pay")) {
            if (mayChangeOrder(order) && order.getTotalCost() == amount) {
                respondToPaymentChange(orderId, userId, true, "pay", replicaId, paymentKey, -1.0);
                setPaid(order, true, paymentKey);
            } else {
                respondToPaymentChange(orderId, userId, false, "pay", replicaId, paymentKey, amount);
            }
        } else if (type.contains("cancel")) {
            if (mayCancelOrder(order)) {
                respondToPaymentChange(orderId, userId, true, "cancel", replicaId, paymentKey, -1.0);
                setPaid(order, false, paymentKey);
            } else {
                respondToPaymentChange(orderId, userId, false, "cancel", replicaId, paymentKey, -1.0);
            }

        }
    }

    private void respondToPaymentChange(int orderId, int userId, boolean result, String type, String replicaId, int paymentKey, double refund) {
        int partition = Partitioner.getPartition(userId, Partitioner.Service.PAYMENT, env);
        Map<String, Object> data;
        if (refund != -1.0 && !result && type == "pay") {
            data = Map.of( "orderId", orderId, "userId", userId, "result", result, "type", type, "replicaId", replicaId, "paymentKey", paymentKey, "refund", refund);
        } else {
            data = Map.of( "orderId", orderId, "userId", userId, "result", result, "type", type, "replicaId", replicaId, "paymentKey", paymentKey);
        }
        Template.send("toPaymentResponse", partition, orderId, data);
    }

    private void setPaid(Order order, boolean paid, int paymentKey) {
        order.setPaid(paid);
        Set<Integer> processedPaymentKeys = order.getProcessedPaymentKeys();
        processedPaymentKeys.add(paymentKey);
        order.setProcessedPaymentKeys(processedPaymentKeys);
        repository.save(order);
    }

    public void sendOrderExists(Order order, String method) {
        int userId = order.getUserId();
        int partition = Partitioner.getPartition(userId, Partitioner.Service.PAYMENT, env);

        Map<String, Object> data = Map.of("orderId", order.getOrderId(env), "userId", userId, "method", method);

        Template.send("toPaymentOrderExists", partition, order.getOrderId(env), data);
    }

    private boolean mayChangeOrder(Order order) {
        return !order.isInCheckout() && !order.isPaid();
    }

    private boolean mayCancelOrder(Order order) {
        return order.isPaid() && !order.isInCheckout();
    }

    private boolean mayCheckout(Order order) { return !order.isInCheckout() && order.getItems().size() > 0;}
}
