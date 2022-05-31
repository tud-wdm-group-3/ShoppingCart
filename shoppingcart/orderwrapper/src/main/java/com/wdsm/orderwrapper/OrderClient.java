package com.wdsm.orderwrapper;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(name="order",path="orders")
public interface OrderClient {

    @PostMapping("/create/{userId}")
    String clientCreate(@PathVariable(name="userId") int userId);


}
