package com.wdsm.paymentwrapper;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.wsdm.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController
@RequiredArgsConstructor
public class PaymentWrapperController {

    private final PaymentWrapperService paymentWrapperService;
    @Autowired
    private EurekaClient eurekaClient;

    @PostMapping(path="pay/{user_id}/{order_id}/{amount}")
    public ResponseEntity pay(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId, @PathVariable("amount") Integer amount){
        return paymentWrapperService.payOrder(userId,orderId,amount,figureOutPartition(userId));

    }

    @PostMapping(path="cancel/{user_id}/{order_id}")
    public ResponseEntity cancel(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId){
        return paymentWrapperService.cancelOrderPayment(userId,orderId,figureOutPartition(userId));
    }

    @GetMapping(path="status/{user_id}/{order_id}")
    public Object getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId){
        return paymentWrapperService.getOrderPaymentStatus(userId,orderId,figureOutPartition(userId));
    }
    //no partitioning needed
    @PostMapping(path="create_user")
    public Map<String, Integer> registerUser(){
        return paymentWrapperService.createUser(figureOutPartition(-1));
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public Object addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount){
        return paymentWrapperService.addFundsToAccount(userId,amount,figureOutPartition(userId));
    }

    @GetMapping(path="find_user/{user_id}")
    public Optional<Payment> findUser(@PathVariable("user_id") Integer userId){
        return paymentWrapperService.findUser(userId,figureOutPartition(userId));
    }
    //see orderwrapper javadoc for relevant info
    int figureOutPartition(int id){
        Applications apps=eurekaClient.getApplications();
        int sum=0;
        for(Application app:apps.getRegisteredApplications()){
            String[] name=app.getName().split("-");
            if(name[0].equalsIgnoreCase("payment"))
                sum++;
        }
        if(id<0){
            Random r=new Random();
            return r.nextInt(sum);
        }else
            return (id%sum);
    }
}
