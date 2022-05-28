package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Future;

@Service
public class OrderService {
    final OrderRepository repository;

    TransactionHandler transactionHandler;

    @Autowired
    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public int createOrder(int userId){
        Order order=new Order();
        order.setUserId(userId);
        repository.save(order);
        return order.getOrderId();
    }

    public void deleteOrder(int orderId){
        repository.findById(orderId).ifPresent(repository::delete);
    }

    public Optional<Order> findOrder(int orderId){
        return repository.findById(orderId);
    }

    public void addItemToOrder(int orderId,int itemId){
        Optional<Order> res = repository.findById(orderId);
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

    public boolean checkout(Order order){
        boolean result = transactionHandler.startCheckout(order);
        return true; // TODO: How to do this non-blocking?
    }
}
