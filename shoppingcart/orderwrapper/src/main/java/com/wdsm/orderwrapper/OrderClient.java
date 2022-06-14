package com.wdsm.orderwrapper;

import com.wsdm.order.Order;
import feign.RequestLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URI;

@FeignClient(name="Order")
public interface OrderClient {

    @PostMapping("orders/create/{userId}")
    //@RequestLine("POST")
    public String create(@PathVariable(name = "userId") int userId, URI baseUri);

    @DeleteMapping(path = "orders/remove/{orderId}")
    void remove(@PathVariable(name="orderId") int orderId);

    @GetMapping(path = "orders/find/{orderId}")
    public Order find(@PathVariable(name="orderId") int orderId,URI baseUri);

    @PostMapping(path = "orders/addItem/{orderId}/{itemId}")
    void addItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId);

    @DeleteMapping(path = "orders/removeItem/{orderId}/{itemId}")
    void removeItem(@PathVariable(name="orderId") int orderId,
                           @PathVariable(name="itemId") int itemId);

    @PostMapping(path = "orders/checkout/{orderId}")
    ResponseEntity checkout(@PathVariable(name="orderId") int orderId);

}
