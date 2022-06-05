package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;


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

        return globalId;
    }

    public void deleteOrder(int orderId){
        findOrder(orderId).ifPresent(repository::delete);
    }

    public Optional<Order> findOrder(int orderId){
        // Convert to local id
        int localId = (orderId - Environment.myOrderInstanceId) / Environment.numOrderInstances;
        return repository.findById(localId);
    }

    public void addItemToOrder(int orderId, int itemId){
        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            List<Integer> items = order.getItems();
            if (!items.contains(itemId)) {
                items.add(itemId);
                repository.save(order);
            }
        }
    }

    public void removeItemFromOrder(int orderId,int itemId){
        Optional<Order> res = findOrder(orderId);
        if(res.isPresent()) {
            Order order = res.get();
            List<Integer> items = order.getItems();
            if (items.contains(itemId)) {
                items.remove(itemId);
                repository.save(order);
            }
        }
    }

    public void checkout(Order order, DeferredResult<ResponseEntity> response){
        transactionHandler.startCheckout(order, response);
    }
}
