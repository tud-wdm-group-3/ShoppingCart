package com.wsdm.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("payment")
public class PaymentUserController extends KafkaSubscriber {

    @Autowired
    PaymentUserService paymentUserService;

    @PostMapping(path="create_user")
    public Integer registerUser() {
        // TODO: return JSON instead of just a single int
        return paymentUserService.registerUser();
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public boolean addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount) {
        return paymentUserService.addFunds(userId, amount);
    }

    @GetMapping(path="find_user/{user_id}")
    public PaymentUser findUser(@PathVariable("user_id") Integer userId) {
        return paymentUserService.findUser(userId);
    }
}
