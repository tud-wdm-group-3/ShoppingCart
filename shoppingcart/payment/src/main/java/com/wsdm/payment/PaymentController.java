package com.wsdm.payment;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.NotYetImplementedException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("api/v1/payment")
public record PaymentController(PaymentService paymentService) {

    @PostMapping(path="create_user")
    public Integer registerUser() {
        /* create user with 0 credit */
        // TODO: return JSON instead of just a single int
        return paymentService.registerUser();
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public boolean addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount) {
        /* adds funds (amount) to the user's (user_id) account */
        return paymentService.addFunds(userId, amount);
    }

    @GetMapping(path="find_user/{user_id}")
    public Payment findUser(@PathVariable("user_id") Integer userId) {
        /* returns the user information */
        return paymentService.findUser(userId);
    }

    @PostMapping(path="pay/{user_id}/{order_id}/{amount}")
    public void payOrder(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId,
                         @PathVariable("amount") Integer amount) {
        /* subtracts the amount of the order from the user's credit (returnsj failture if credit is not enought) */
        throw new NotYetImplementedException();
    }

    @PostMapping(path="cancel/{user_id}/{order_id}")
    public void cancelOrder(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        /* cancels the payment made by a specific user for a specific order */
        throw new NotYetImplementedException();
    }

    @GetMapping(path="status/{user_id}/{order_id}")
    public void getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId) {
        /* returns the status of the payment (paid or not) */
        throw new NotYetImplementedException();
    }
}
