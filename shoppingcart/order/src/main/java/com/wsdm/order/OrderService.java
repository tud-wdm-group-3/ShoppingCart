package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;


import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Future;

@Service
public class OrderService {

    final OrderRepository repository;

    @Autowired
    private TransactionHandler transactionHandler;

    @Autowired
    public OrderService(OrderRepository repository) {
        this.repository = repository;
        transactionHandler = new TransactionHandler(repository);
    }

    public int createOrder(int userId){
        Order order=new Order();
        order.setUserId(userId);
        repository.save(order);

        int globalId = order.getLocalId() * Environment.numOrderInstances + Environment.myOrderInstanceId;
        order.setOrderId(globalId);

        transactionHandler.sendOrderExists(globalId, 0);

        return globalId;
    }

    public boolean deleteOrder(int orderId){
        transactionHandler.sendOrderExists(orderId, 1);
        Optional<Order> optOrder = findOrder(orderId);
        if (optOrder.isPresent()) {
            Order order = optOrder.get();
            repository.delete(order);
            return true;
        }
        return false;
    }

    public Optional<Order> findOrder(int orderId){
        // Convert to local id
        int localId = (orderId - Environment.myOrderInstanceId) / Environment.numOrderInstances;
        return repository.findById(localId);
    }

    public boolean addItemToOrder(int orderId, int itemId){
        // Check if itemId exists
        if (!transactionHandler.itemExists(itemId)) return false;

        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            if (!order.isPaid()) {
                List<Integer> items = order.getItems();
                items.add(itemId);

                // Increase order's total cost
                int price = transactionHandler.getItemPrice(itemId);
                order.setTotalCost(order.getTotalCost() + price);

                repository.save(order);
                return true;
            }
        }
        return false;
    }

    public boolean removeItemFromOrder(int orderId,int itemId){
        // Check if itemId exists
        if (!transactionHandler.itemExists(itemId)) return false;

        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            if (!order.isPaid()) {
                List<Integer> items = order.getItems();
                if (items.contains(itemId)) {
                    items.remove(itemId);

                    // Decrease order's total cost
                    int price = transactionHandler.getItemPrice(itemId);
                    order.setTotalCost(order.getTotalCost() - price);

                    repository.save(order);
                    return true;
                }
            }
        }
        return false;
    }

    public void checkout(Order order, DeferredResult<ResponseEntity> response){
        transactionHandler.startCheckout(order, response);
    }
}
