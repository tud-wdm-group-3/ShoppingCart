package com.wsdm.order;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("orders")
public record OrderController() {

    @PostMapping(path = "/create/{user_id}")
    public void create(@PathVariable(name="user_id") int user_id) {
        Order order = Order.builder()
                .user_id(user_id)
                .build();
        // TODO: add more stuff to build
    }

    @DeleteMapping(path = "/remove/{order_id}")
    public void remove(@PathVariable(name="order_id") String order_id) {
        return;
    }

    @GetMapping(path = "/find/{order_id}")
    public void find(@PathVariable(name="order_id") String order_id) {
        return;
    }

    @PostMapping(path = "/addItem/{order_id}/{item_id}")
    public void addItem(@PathVariable(name="order_id") String order_id,
                       @PathVariable(name="item_id") String item_id) {
        return;
    }

    @DeleteMapping(path = "/removeItem/{order_id}/{item_id}")
    public void removeItem(@PathVariable(name="order_id") String order_id,
                        @PathVariable(name="item_id") String item_id) {
        return;
    }

    @PostMapping(path = "/checkout/{order_id}")
    public void checkout(@PathVariable(name="order_id") String order_id) {
        return;
    }
}
