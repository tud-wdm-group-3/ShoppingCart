package com.wsdm.order;


import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class OrderService {
    final
    OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public int createOrder(int user_id){
        Order order=new Order();
        order.setUser_id(user_id);
        repository.save(order);
        return order.getOrder_id();
    }

    public void deleteOrder(int order_id){
        repository.findById(order_id).ifPresent(repository::delete);
    }

    public Optional<Order> findOrder(int order_id){
        return repository.findById(order_id);
    }

    public void addItemToOrder(int order_id,int item_id){
        Optional<Order> res=repository.findById(order_id);
        if(res.isPresent()) {
            Order order = res.get();
            List<Integer> items = order.getItems();
            if (!items.contains(item_id)) {
                items.add(item_id);
                repository.save(order);
            }
        }
    }

    public void removeItemFromOrder(int order_id,int item_id){
        Optional<Order> res=repository.findById(order_id);
        if(res.isPresent()) {
            Order order = res.get();
            List<Integer> items = order.getItems();
            if (items.contains(item_id)) {
                items.remove(item_id);
                repository.save(order);
            }
        }
    }

    public boolean checkout(int order_id){
        // TODO: final call to "payment" and "stock" services for completing the order
        return new Random().nextBoolean();
    }
}
