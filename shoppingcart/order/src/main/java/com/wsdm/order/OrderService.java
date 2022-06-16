package com.wsdm.order;

import com.wsdm.order.utils.Partitioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
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
    @Value("${PARTITION_ID}")
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
        try {
            myReplicaId = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public int createOrder(int userId){
        Order order=new Order();
        order.setUserId(userId);
        repository.save(order);

        int globalId = order.getLocalId() * numOrderInstances + myOrderInstanceId;
        order.setOrderId(globalId);
        repository.save(order);

        transactionHandler.sendOrderExists(order, 0);

        return globalId;
    }

    public boolean deleteOrder(int orderId){
        Optional<Order> optOrder = findOrder(orderId);
        if (optOrder.isPresent()) {
            Order order = optOrder.get();
            if (mayChangeOrder(order)) {
                transactionHandler.sendOrderExists(order, 1);
                repository.delete(order);
                return true;
            }
        }
        return false;
    }

    public Optional<Order> findOrder(int orderId) {
        // Convert to local id
        int localId = (orderId - myOrderInstanceId) / numOrderInstances;
        return repository.findById(localId);
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
     * Used to initialize cache of itemIds, so false relativeToCurrent.
     */
    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "fromStockItemPrice",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "false")}))
    private void getItemPrice(Map<String, Integer> item) {
        int itemId = item.get("itemId");
        int price = item.get("price");
        System.out.println("Received item cache " + itemId + " with price " + price);

        itemPrices.put(itemId, price);
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
    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "fromPaymentPaid",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "true")}))
    private void paymentChanged(Map<String, Object> request) {
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        int amount = (int) request.get("amount");
        String type = (String) request.get("type");
        System.out.println("Received payment made/cancelled with order " + orderId + " from user " + userId + " and amount " + amount);

        Optional<Order> optOrder = findOrder(orderId);

        if (!optOrder.isPresent()) {
            throw new AssertionError("Payment made for unexisting order");
        }

        Order order = optOrder.get();

        int partition = Partitioner.getPartition(userId, numPaymentInstances);
        if (type == "pay") {
            if (mayChangeOrder(order) && order.getTotalCost() == amount) {
                Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "result", true);
                kafkaTemplate.send("toPaymentWasOk", partition, orderId, data);
                order.setPaid(true);
                repository.save(order);
            } else {
                Map<String, Object> data = Map.of( "orderId", orderId, "userId", userId, "result", false,"refund", amount);
                kafkaTemplate.send("toPaymentWasOk", partition, orderId, data);
            }
        } else if (type == "cancel") {
            order.setPaid(false);
            repository.save(order);
        }
    }

    private boolean mayChangeOrder(Order order) {
        return !order.isInCheckout() && !order.isPaid();
    }

    private boolean mayCheckout(Order order) { return !order.isInCheckout();}
}
