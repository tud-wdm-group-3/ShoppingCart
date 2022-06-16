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

    @GetMapping(path = "/dump")
    public List<Order> dump() {
        return service.repository.findAll();
    }
    
    @PostMapping(path = "/create/{userId}")
    public Map<String, Integer> create(@PathVariable(name="userId") int userId) {
        int orderId = service.createOrder(userId);
        System.out.println("Received create order request with userId " + userId + " new order " + orderId);
        return Map.of("order_id", orderId);
    }

    @DeleteMapping(path = "/remove/{orderId}")
    public ResponseEntity remove(@PathVariable(name="orderId") int orderId) {
        System.out.println("Received remove order request with orderId " + orderId);
        boolean completed = service.deleteOrder(orderId);
        if (!completed)
            return ResponseEntity.notFound().build();
        else
            return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/find/{orderId}")
    public Optional<Order> find(@PathVariable(name="orderId") int orderId) {
        System.out.println("Received find order request with orderId " + orderId);
        return service.findOrder(orderId);
    }

    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public ResponseEntity addItem(@PathVariable(name="orderId") int orderId,
                       @PathVariable(name="itemId") int itemId) {
        System.out.println("Add item " + itemId + " to orderId " + orderId);
        boolean completed = service.addItemToOrder(orderId,itemId);
        ResponseEntity response = ResponseEntity.ok().build();
        if (!completed) {
            response = ResponseEntity.badRequest().build();
        }
        return response;
    }

    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public ResponseEntity removeItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        System.out.println("Remove item " + itemId + " to orderId " + orderId);
        boolean completed = service.removeItemFromOrder(orderId, itemId);
        ResponseEntity response = ResponseEntity.ok().build();
        if (!completed) {
            response = ResponseEntity.badRequest().build();
        }
        return response;
    }

    @PostMapping(path = "/checkout/{orderId}")
    public DeferredResult<ResponseEntity> checkout(@PathVariable(name="orderId") int orderId) {
        System.out.println("Checkout order " + orderId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        service.checkout(orderId, response);
        return response;
    }
}
