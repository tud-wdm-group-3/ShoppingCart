package com.wsdm.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("payment")
public class PaymentController {

    @Autowired
    PaymentService paymentService;

    @GetMapping(path = "/dump")
    public List<Payment> dump() {
        return paymentService.paymentRepository.findAll();
    }

    @PostMapping(path="pay/{user_id}/{order_id}/{amount}")
    public DeferredResult<ResponseEntity> pay(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId, @PathVariable("amount") Double amount) {
        System.out.println("Received pay on from user " + userId + " for order " + orderId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        paymentService.makePayment(userId, orderId, amount, response);

        return response;
    }

    @PostMapping(path="cancel/{user_id}/{order_id}")
    public ResponseEntity cancel(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        System.out.println("Received cancel from user " + userId + " for order " + orderId);
        boolean completed = paymentService.cancelPayment(userId, orderId);
        if (!completed) {
            ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(path="status/{user_id}/{order_id}")
    public Object getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        System.out.println("Get status from user " + userId + " and order " + orderId);
        return paymentService.getPaymentStatus(userId, orderId);
    }

    @PostMapping(path="create_user")
    public Map<String, Integer> registerUser() {
        int userId = paymentService.registerUser();
        System.out.println("Registered user with userId " + userId);
        return Map.of("user_id", userId);
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public boolean addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Double amount) {
        System.out.println("Adding " + amount + " to funds of user " + userId);
        return paymentService.addFunds(userId, amount);
    }

    @GetMapping(path="find_user/{user_id}")
    public Optional<Payment> findUser(@PathVariable("user_id") Integer userId) {
        System.out.println("Finding user " + userId);
        return paymentService.findUser(userId);
    }
}
