package com.wsdm.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("payment")
public class PaymentController {

    @Autowired
    PaymentService paymentService;

    @PostMapping(path="pay/{user_id}/{order_id}/{amount}")
    public DeferredResult<ResponseEntity> pay(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId, @PathVariable("amount") Integer amount) {
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        boolean completed = paymentService.makePayment(userId, orderId, amount);
        if (!completed) {
            response.setResult(ResponseEntity.badRequest().build());
        }
        return response;
    }

    @PostMapping(path="cancel/{user_id}/{order_id}")
    public DeferredResult<ResponseEntity> cancel(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        DeferredResult<ResponseEntity> response = new DeferredResult<>();
        boolean completed = paymentService.cancelPayment(userId, orderId);
        if (!completed) {
            response.setResult(ResponseEntity.badRequest().build());
        }
        return response;
    }

    @GetMapping(path="status/{user_id}/{order_id}")
    public Object getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        return paymentService.getPaymentStatus(userId, orderId);
    }

    @PostMapping(path="create_user")
    public Map<String, Integer> registerUser() {
        return Map.of("user_id", paymentService.registerUser());
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public Object addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount) {
        if (amount <= 0){
            DeferredResult<ResponseEntity> response = new DeferredResult<>();
            response.setResult(ResponseEntity.badRequest().build());
            return response;
        }
        boolean completed =  paymentService.addFunds(userId, amount);
        return Map.of("done", completed);
    }

    @GetMapping(path="find_user/{user_id}")
    public Optional<Payment> findUser(@PathVariable("user_id") Integer userId) {
        return paymentService.findUser(userId);
    }
}
