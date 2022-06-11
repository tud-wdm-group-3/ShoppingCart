package com.wdsm.paymentwrapper;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.Optional;

@Component
public class PaymentClient {
    FeignClientBuilder feignClientBuilder;
    public PaymentClient(@Autowired ApplicationContext applicationContext){
        this.feignClientBuilder=new FeignClientBuilder(applicationContext);
    }

    interface CustomCall {
        @PostMapping(path="pay/{user_id}/{order_id}/{amount}")
        DeferredResult<ResponseEntity> pay(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId, @PathVariable("amount") Integer amount);

        @PostMapping(path="cancel/{user_id}/{order_id}")
        DeferredResult<ResponseEntity> cancel(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId);

        @GetMapping(path="status/{user_id}/{order_id}")
        Object getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId);

        @PostMapping(path="create_user")
        Map<String, Integer> registerUser();

        @PostMapping(path="add_funds/{user_id}/{amount}")
        Object addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount);

        @GetMapping(path="find_user/{user_id}")
        Optional<Payment> findUser(@PathVariable("user_id") Integer userId);
    }

    public DeferredResult<ResponseEntity> payOrder(Integer userId,Integer orderId, Integer amount,int partitionId){
        return delegate(partitionId).pay(userId,orderId,amount);
    }

    public DeferredResult<ResponseEntity> cancelOrderPayment(Integer userId, Integer orderId,int partitionId){
        return delegate(partitionId).cancel(userId,orderId);
    }

    public Object getOrderPaymentStatus(Integer userId,Integer orderId,int partitionId){
        return delegate(partitionId).getStatus(userId,orderId);
    }

    public Map<String,Integer> createUser(int partitionId){
        return delegate(partitionId).registerUser();
    }

    public Object addFundsToAccount(Integer userId,Integer amount,int partitionId){
        return delegate(partitionId).addFunds(userId,amount);
    }

    public Optional<Payment> findUser(Integer userId,int partitionId){
        return delegate(partitionId).findUser(userId);
    }

    public CustomCall delegate(int partitionId){
        String serviceName="payment-"+partitionId;
        return this.feignClientBuilder.forType(CustomCall.class,serviceName).build();
    }
}
