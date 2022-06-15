package com.wdsm.paymentwrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

@RestController
@RequiredArgsConstructor
public class PaymentWrapperController {

    private static final String baseUri="http://payment-";
    @Value("${partitions}")
    private int partitions;

    @PostMapping(path="pay/{user_id}/{order_id}/{amount}")
    public ResponseEntity pay(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId, @PathVariable("amount") Integer amount){
        try{
            int partition = figureOutPartition(userId);
            System.out.println("In paymentwrapper, requesting pay for userid "+userId+" and order "+orderId+" with amount "+amount+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/payment/pay/"+userId+"/"+orderId+"/"+amount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In paymentwrapper, exception while requesting pay for userid "+userId+" and order "+orderId+" with amount"+amount);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }

    }

    @PostMapping(path="cancel/{user_id}/{order_id}")
    public ResponseEntity cancel(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId){
        try{
            int partition=figureOutPartition(userId);
            System.out.println("In paymentwrapper, requesting cancel order for user "+userId+" and order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/payment/cancel/"+userId+"/"+orderId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In paymentwrapper, exception while requesting cancel order for user "+userId+" and order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(path="status/{user_id}/{order_id}")
    public ResponseEntity getStatus(@PathVariable("user_id") Integer userId, @PathVariable("order_id") Integer orderId){
        try{
            int partition=figureOutPartition(userId);
            System.out.println("In paymentwrapper, requesting status for user "+userId+" and order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/payment/status/"+userId+"/"+orderId))
                    .GET()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In paymentwrapper, exception while requesting status for user "+userId+" and order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    //no partitioning needed
    @PostMapping(path="create_user")
    public ResponseEntity registerUser(){
        try{
            int partition=figureOutPartition(-1);
            System.out.println("In paymentwrapper, requesting create user, calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/payment/create_user"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In paymentwrapper, exception requesting create user");
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="add_funds/{user_id}/{amount}")
    public ResponseEntity addFunds(@PathVariable("user_id") Integer userId, @PathVariable("amount") Integer amount){
        try{
            int partition = figureOutPartition(userId);
            System.out.println("In paymentwrapper, requesting add funds for user "+userId+" and amount "+amount+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/payment/add_funds/"+userId+"/"+amount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(path="find_user/{user_id}")
    public ResponseEntity findUser(@PathVariable("user_id") Integer userId){
        try{
            int partition = figureOutPartition(userId);
            System.out.println("In paymentwrapper, requesting find user "+userId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/payment/find_user/"+userId))
                    .GET()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In paymentwrapper, exception requesting find user "+userId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    //see orderwrapper javadoc for relevant info
    int figureOutPartition(int id){
        if(id<0){
            Random r=new Random();
            return r.nextInt(partitions);
        }else
            return (id%partitions);
    }
}
