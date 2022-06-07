package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;

@RestController
@RequestMapping("orders")
public class OrderController {

    @Autowired
    OrderService service;
    
    @PostMapping(path = "/create/{userId}")
    public Map<String, Integer> create(@PathVariable(name="userId") int userId) {
        // TODO: check if user exists in payment's db and return ResponseEntity
        return Map.of("order_id", service.createOrder(userId));
    }

    @DeleteMapping(path = "/remove/{orderId}")
    public ResponseEntity remove(@PathVariable(name="orderId") int orderId) {
        boolean completed = service.deleteOrder(orderId);
        if (!completed)
            return ResponseEntity.notFound().build();
        else
            return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/find/{orderId}")
    public Optional<Order> find(@PathVariable(name="orderId") int orderId) {
        return service.findOrder(orderId);
    }

    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public DeferredResult<ResponseEntity> addItem(@PathVariable(name="orderId") int orderId,
                       @PathVariable(name="itemId") int itemId) {
        boolean completed = service.addItemToOrder(orderId,itemId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        if (!completed) {
            response.setResult(ResponseEntity.badRequest().build());
        }
        return response;
    }

    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public DeferredResult<ResponseEntity> removeItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        boolean completed = service.removeItemFromOrder(orderId, itemId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        if (!completed) {
            response.setResult(ResponseEntity.badRequest().build());
        }
        return response;
    }

    @PostMapping(path = "/checkout/{orderId}")
    public DeferredResult<ResponseEntity> checkout(@PathVariable(name="orderId") int orderId) {
        Optional<Order> order = service.findOrder(orderId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        if (!order.isPresent()) {
            response.setResult(ResponseEntity.notFound().build());
        } else {
            service.checkout(order.get(), response);
        }
        return response;
    }
}
