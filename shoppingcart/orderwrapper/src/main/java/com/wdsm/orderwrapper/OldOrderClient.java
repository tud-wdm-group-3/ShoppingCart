package com.wdsm.orderwrapper;

import com.wsdm.order.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Component
public class OldOrderClient {
    FeignClientBuilder feignClientBuilder;
    public OldOrderClient(@Autowired ApplicationContext applicationContext){
        this.feignClientBuilder=new FeignClientBuilder(applicationContext);
    }

    interface CustomCall {
        @PostMapping("orders/create/{userId}")
        String create(@PathVariable(name = "userId") int userId);

        @DeleteMapping(path = "orders/remove/{orderId}")
        void remove(@PathVariable(name="orderId") int orderId);

        @GetMapping(path = "orders/find/{orderId}")
        Order find(@PathVariable(name="orderId") int orderId);

        @PostMapping(path = "orders/addItem/{orderId}/{itemId}")
        void addItem(@PathVariable(name="orderId") int orderId,
                            @PathVariable(name="itemId") int itemId);

        @DeleteMapping(path = "orders/removeItem/{orderId}/{itemId}")
        void removeItem(@PathVariable(name="orderId") int orderId,
                               @PathVariable(name="itemId") int itemId);

        @PostMapping(path = "orders/checkout/{orderId}")
        ResponseEntity checkout(@PathVariable(name="orderId") int orderId);
    }
    public String createOrder(int userId, int partitionId){
        return delegate(partitionId).create(userId);
    }

    public void removeOrder(int orderId,int partitionId){
        delegate(partitionId).remove(orderId);
    }

    public Order findOrder(int orderId,int partitionId){
        return delegate(partitionId).find(orderId);
    }

    public void addItemInOrder(int orderId,int itemId,int partitionId){
        delegate(partitionId).addItem(orderId,itemId);
    }

    public void removeItemFromOrder(int orderId,int itemId,int partitionId){
        delegate(partitionId).removeItem(orderId,itemId);
    }

    public ResponseEntity checkoutOrder(int orderId,int partitionId){
        return delegate(partitionId).checkout(orderId);
    }

    public CustomCall delegate(int partitionId){
        String serviceName="http://order-"+partitionId;
        return this.feignClientBuilder.forType(CustomCall.class,serviceName).build();
    }

}
