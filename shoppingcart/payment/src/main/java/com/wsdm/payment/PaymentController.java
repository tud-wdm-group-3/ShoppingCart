package com.wsdm.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.http.MediaType;

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
        return paymentService.dump();
    }

    @PostMapping(path="pay/{user_id}/{order_id}/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ResponseEntity> pay(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId, @PathVariable("amount") Double amount) {
        System.out.println("Received pay on from user " + userId + " for order " + orderId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        paymentService.changePayment(userId, orderId, amount, response, false);

        return response;
    }


    @PostMapping(path="cancel/{user_id}/{order_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ResponseEntity> cancel(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        System.out.println("Received cancel from user " + userId + " for order " + orderId);
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        paymentService.changePayment(userId, orderId, -1, response, true);
        return response;
    }

    @GetMapping(path="status/{user_id}/{order_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        System.out.println("Get status from user " + userId + " and order " + orderId);
        return paymentService.getPaymentStatus(userId, orderId);
    }

    @PostMapping(path="create_user", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Integer> registerUser() {
        int userId = paymentService.registerUser();
        System.out.println("Registered user with userId " + userId);
        return Map.of("user_id", userId);
    }

    @PostMapping(path="add_funds/{user_id}/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Boolean> addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount) {
        System.out.println("Adding " + amount + " to funds of user " + userId);
        return Map.of("done", paymentService.addFunds(userId, amount));
    }

    @GetMapping(path="find_user/{user_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object findUser(@PathVariable("user_id") Integer userId) {
        System.out.println("Finding user " + userId);
        Optional<Payment> optPayment = paymentService.findUser(userId);
        if (optPayment.isPresent()) {
            Payment payment = optPayment.get();
            return Map.of("user_id", payment.getUserId(), "credit", payment.getCredit());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
