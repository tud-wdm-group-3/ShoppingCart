package com.wsdm.order;

import com.wsdm.order.utils.NameUtils;
import com.wsdm.order.utils.Partitioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
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
@Transactional(isolation = Isolation.SERIALIZABLE)
public class OrderService {
    @Value("${PARTITION}")
    private int myOrderInstanceId;

    private String myReplicaId;

    private int numStockInstances = 2;

    private int numPaymentInstances = 2;

    private int numOrderInstances = 2;

    final OrderRepository repository;

    @Autowired
    private TransactionHandler transactionHandler;

    @Autowired
    public OrderService(OrderRepository repository) {
        this.repository = repository;
        myReplicaId = NameUtils.getHostname();
        System.out.println("Order service started with replica-id " + myReplicaId);

        List<Order> ordersNotBroadcasted = repository.findOrdersByOrderBroadcastedIsNot(Order.OrderBroadcasted.YES);
        List<Order> ordersToSave = new ArrayList<>();
        for (Order orderNotBroadcasted : ordersNotBroadcasted) {
            if (orderNotBroadcasted.getOrderBroadcasted() == Order.OrderBroadcasted.NO) {
                // this means we failed at creation before returning to client, so we delete this order
                orderNotBroadcasted.setOrderBroadcasted(Order.OrderBroadcasted.DELETED);
                ordersToSave.add(orderNotBroadcasted);
            } else if (orderNotBroadcasted.getOrderBroadcasted() == Order.OrderBroadcasted.PROCESSING_DELETION) {
                // this means we failed at deletion before returning to client, so we rebroadcast and do not delete
                sendOrderExists(orderNotBroadcasted, "create");
                orderNotBroadcasted.setOrderBroadcasted(Order.OrderBroadcasted.YES);
                ordersToSave.add(orderNotBroadcasted);
            }
        }
        repository.saveAll(ordersToSave);
    }

    public List<Order> dump() {
        return this.repository.findAll();
    }

    public int createOrder(int userId){
        Order order=new Order(userId);
        repository.save(order);

        int globalId = order.getLocalId() * numOrderInstances + myOrderInstanceId;
        order.setOrderId(globalId);
        order.setOrderBroadcasted(Order.OrderBroadcasted.YES);
        sendOrderExists(order, "create");
        repository.save(order);

        return globalId;
    }

    public boolean deleteOrder(int orderId){
        Optional<Order> optOrder = findOrder(orderId);
        if (optOrder.isPresent()) {
            Order order = optOrder.get();
            if (mayChangeOrder(order)) {
                order.setOrderBroadcasted(Order.OrderBroadcasted.PROCESSING_DELETION);
                repository.save(order);
                sendOrderExists(order, "delete");
                order.setOrderBroadcasted(Order.OrderBroadcasted.DELETED);
                repository.save(order);
                return true;
            }
        }
        return false;
    }

    public Optional<Order> findOrder(int orderId) {
        // Convert to local id
        int localId = (orderId - myOrderInstanceId) / numOrderInstances;
        Optional<Order> optOrder = repository.findById(localId);

        if (optOrder.isPresent()) {
            Order order = optOrder.get();
            if (order.getOrderBroadcasted() == Order.OrderBroadcasted.DELETED) {
                // do not return deleted orders
                optOrder = Optional.empty();
            }
        }
        return optOrder;
    }

    public boolean addItemToOrder(int orderId, int itemId){
        // Check if itemId exists
        if (!itemExists(itemId)) return false;

        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            if (mayChangeOrder(order)) {
                List<Integer> items = order.getItems();
                items.add(itemId);
                order.setItems(items);

                // Increase order's total cost
                int price = getItemPrice(itemId);
                order.setTotalCost(order.getTotalCost() + price);

                repository.save(order);
                return true;
            }
        }
        return false;
    }

    public boolean removeItemFromOrder(int orderId,int itemId){
        // Check if itemId exists
        if (!itemExists(itemId)) return false;

        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            if (mayChangeOrder(order)) {
                List<Integer> items = order.getItems();
                if (items.contains(itemId)) {
                    items.remove(itemId);

                    // Decrease order's total cost
                    int price = getItemPrice(itemId);
                    order.setTotalCost(order.getTotalCost() - price);

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

    @Autowired
    private KafkaTemplate<Integer, Object> kafkaTemplate;

    /**
     * Map itemdId to price.
     */
    private Map<Integer, Integer> itemPrices = new HashMap<>();

    /**
     * Used to initialize cache of itemIds, so false relativeToCurrent, and partition 0.
     */
    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromStockItemPrice",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "false")}))
    private void getItemPrice(Map<String, Integer> item) {
        int itemId = item.get("itemId");
        int price = item.get("price");
        System.out.println("Received item cache " + itemId + " with price " + price);

        if (this.itemPrices == null) {
            System.out.println("map is null again");
            this.itemPrices = new HashMap<>();
        }
        this.itemPrices.put(itemId, price);
    }

    public boolean itemExists(int itemId) {
        return itemPrices.containsKey(itemId);
    }

    public int getItemPrice(int itemId) {
        return itemPrices.get(itemId);
    }

    /**
     * Payment made or cancelled.
     * By putting them in the same topic, we have a total ordering between pay and cancel,
     * so we always process in the correct order.
     */
    @KafkaListener(topicPartitions = @TopicPartition(topic = "fromPaymentPaid",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    private void paymentChanged(Map<String, Object> request) {
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        int amount = (int) request.get("amount");
        String type = (String) request.get("type");
        String replicaId = (String) request.get("replicaId");
        int paymentKey = (int) request.get("paymentKey");

        System.out.println("Received payment made/cancelled with order " + orderId + " from user " + userId + " and amount " + amount);

        Optional<Order> optOrder = findOrder(orderId);

        if (!optOrder.isPresent()) {
            throw new AssertionError("Payment made for unexisting order");
        }

        Order order = optOrder.get();

        // Check if other replicas have processed this already
        if (order.getProcessedPaymentKeys().contains(paymentKey)) {
            return;
        }

        if (type == "pay") {
            if (mayChangeOrder(order) && order.getTotalCost() == amount) {
                respondToPaymentChange(orderId, userId, true, "pay", replicaId, paymentKey, -1);
                setPaid(order, true, paymentKey);
            } else {
                respondToPaymentChange(orderId, userId, false, "pay", replicaId, paymentKey, amount);
            }
        } else if (type == "cancel") {
            if (mayCancelOrder(order)) {
                respondToPaymentChange(orderId, userId, true, "cancel", replicaId, paymentKey, -1);
                setPaid(order, false, paymentKey);
            } else {
                respondToPaymentChange(orderId, userId, false, "cancel", replicaId, paymentKey, -1);
            }

        }
    }

    private void respondToPaymentChange(int orderId, int userId, boolean result, String type, String replicaId, int paymentKey, int refund) {
        int partition = Partitioner.getPartition(userId, numPaymentInstances);
        Map<String, Object> data = Map.of( "orderId", orderId, "userId", userId, "result", result, "type", type, "replicaId", replicaId, "paymentKey", paymentKey);
        if (refund != -1 && !result && type == "pay") {
            data.put("refund", refund);
        }
        kafkaTemplate.send("toPaymentResponse", partition, orderId, data);
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
        int partition = Partitioner.getPartition(userId, numPaymentInstances);

        Map<String, Object> data = Map.of("orderId", order.getOrderId(), "userId", userId, "method", method);
        kafkaTemplate.send("toPaymentOrderExists", partition, order.getOrderId(), data);
    }

    private boolean mayChangeOrder(Order order) {
        return !order.isInCheckout() && !order.isPaid();
    }

    private boolean mayCancelOrder(Order order) {
        return order.isPaid() && !order.isInCheckout();
    }

    private boolean mayCheckout(Order order) { return !order.isInCheckout();}
}
