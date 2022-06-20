package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;

@RestController
@RequestMapping("orders")
public class OrderController {

    @Autowired
    OrderService service;

    @GetMapping(path = "/dump", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Order> dump() {
        return service.dump();
    }
    
    @PostMapping(path = "/create/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Integer> create(@PathVariable(name="userId") int userId) {
        int orderId = service.createOrder(userId);
        System.out.println("Received create order request with userId " + userId + " new order " + orderId);
        return Map.of("order_id", orderId);
    }

    @DeleteMapping(path = "/remove/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity remove(@PathVariable(name="orderId") int orderId) {
        System.out.println("Received remove order request with orderId " + orderId);
        boolean completed = service.deleteOrder(orderId);
        if (!completed)
            return ResponseEntity.notFound().build();
        else
            return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/find/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object find(@PathVariable(name="orderId") int orderId) {
        System.out.println("Received find order request with orderId " + orderId);
        Optional<Order> optOrder = service.findOrder(orderId);
        if (optOrder.isPresent()) {
            Order order = optOrder.get();
            return Map.of("order_id", order.getOrderId(), "user_id", order.getUserId(), "items", order.getItems(), "paid", order.isPaid(), "total_cost", order.getTotalCost());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(path = "/addItem/{orderId}/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @PostMapping(path = "/checkout/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ResponseEntity> checkout(@PathVariable(name="orderId") int orderId) {
        System.out.println("Checkout order " + orderId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        service.checkout(orderId, response);
        return response;
    }
}
