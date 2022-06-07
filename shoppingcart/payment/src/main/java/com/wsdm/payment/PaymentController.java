package com.wsdm.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("payment")
public class PaymentController {

    @Autowired
    PaymentService paymentService;

    @PostMapping(path="create_user")
    public Map<String, Integer> registerUser() {
        // TODO: return JSON instead of just a single int
        return Map.of("user_id", paymentService.registerUser());
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public boolean addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount) {
        return paymentService.addFunds(userId, amount);
    }

    @GetMapping(path="find_user/{user_id}")
    public Optional<Payment> findUser(@PathVariable("user_id") Integer userId) {
        return paymentService.findUser(userId);
    }
}
