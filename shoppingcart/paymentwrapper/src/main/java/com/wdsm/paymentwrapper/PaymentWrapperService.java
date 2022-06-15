package com.wdsm.paymentwrapper;

import com.wsdm.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentWrapperService {

    private final PaymentClient paymentClient;

    public ResponseEntity payOrder(Integer userId,Integer orderId,Integer amount, int partitionId){
        return paymentClient.payOrder(userId,orderId,amount,partitionId);
    }

    public ResponseEntity cancelOrderPayment(Integer userId, Integer orderId,int partitionId){
        return paymentClient.cancelOrderPayment(userId, orderId, partitionId);
    }

    public Object getOrderPaymentStatus(Integer userId,Integer orderId,int partitionId){
        return paymentClient.getOrderPaymentStatus(userId, orderId, partitionId);
    }

    public Map<String,Integer> createUser(int partitionId){
        return paymentClient.createUser(partitionId);
    }

    public Object addFundsToAccount(Integer userId,Integer amount,int partitionId){
        return paymentClient.addFundsToAccount(userId, amount, partitionId);
    }

    public Optional<Payment> findUser(Integer userId, int partitionId){
        return paymentClient.findUser(userId, partitionId);
    }

}
