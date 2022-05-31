package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("orders")
public class OrderController {

    @Autowired
    OrderService service;
    @PostMapping(path = "/create/{userId}")
    public Map<String,String> create(@PathVariable(name="userId") int userId) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        System.out.println("creating user with id:"+userId+"at "+dtf.format(now));
        HashMap<String,String> output=new HashMap<>();
        output.put("orderId",Integer.toString(service.createOrder(userId)));
        return output;
    }

    @DeleteMapping(path = "/remove/{orderId}")
    public void remove(@PathVariable(name="orderId") int orderId) {
        service.deleteOrder(orderId);
        return;
    }

    @GetMapping(path = "/find/{orderId}")
    public Order find(@PathVariable(name="orderId") int orderId) {
        return service.findOrder(orderId).get();
    }

    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public void addItem(@PathVariable(name="orderId") int orderId,
                       @PathVariable(name="itemId") int itemId) {
        service.addItemToOrder(orderId,itemId);
        return;
    }

    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public void removeItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        service.removeItemFromOrder(orderId, itemId);
        return;
    }

    @PostMapping(path = "/checkout/{orderId}")
    public ResponseEntity checkout(@PathVariable(name="orderId") int orderId) {
        Optional<Order> order = service.findOrder(orderId);
        if (!order.isPresent()) {
            return ResponseEntity.notFound().build();
        } else {
            boolean result = service.checkout(order.get());
            if (result) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.internalServerError().build();
            }
        }
    }
}
