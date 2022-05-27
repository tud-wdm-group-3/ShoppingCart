package com.wsdm.order;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("orders")
public class OrderController {


    @Autowired
    OrderService service;
    @PostMapping(path = "/create/{user_id}")
    public Map<String,String> create(@PathVariable(name="user_id") int user_id) {
        HashMap<String,String> output=new HashMap<>();
        output.put("order_id",Integer.toString(service.createOrder(user_id)));
        return output;
    }

    @DeleteMapping(path = "/remove/{order_id}")
    public void remove(@PathVariable(name="order_id") int order_id) {
        service.deleteOrder(order_id);
        return;
    }

    @GetMapping(path = "/find/{order_id}")
    public Order find(@PathVariable(name="order_id") int order_id) {
        return service.findOrder(order_id).get();
    }

    @GetMapping(path = "/test")
    public String test() {
        this.sendCheckout("test");
        return "check logs!";
    }

    @PostMapping(path = "/addItem/{order_id}/{item_id}")
    public void addItem(@PathVariable(name="order_id") int order_id,
                       @PathVariable(name="item_id") int item_id) {
        service.addItemToOrder(order_id,item_id);
        return;
    }

    @DeleteMapping(path = "/removeItem/{order_id}/{item_id}")
    public void removeItem(@PathVariable(name="order_id") int order_id,
                        @PathVariable(name="item_id") int item_id) {
        service.removeItemFromOrder(order_id,item_id);
        return;
    }

    @PostMapping(path = "/checkout/{order_id}")
    public ResponseEntity checkout(@PathVariable(name="order_id") int order_id) {
        if(service.checkout(order_id))
            return ResponseEntity.ok().build();
        else
            return ResponseEntity.internalServerError().build();
    }


    private static final String TOPIC = "checkout";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendCheckout(Order order) {
        ListenableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(TOPIC, message);

        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                // TODO: Message was received.
            }

            @Override
            public void onFailure(Throwable ex) {
                // TODO: Message failed.
            }
        });
    }
}
