package com.wdsm.orderwrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

@RestController
@RequiredArgsConstructor
public class OrderWrapperController {

    private static final String baseUri="http://order-";
    @Value("${partitions}")
    private int partitions;

    @GetMapping(path="/health")
    public ResponseEntity health(){
        return ResponseEntity.ok().build();
    }


    //doesn't need partitioning
    @PostMapping(value="/create/{userId}")
    public ResponseEntity create(@PathVariable(name="userId") int userId){
        try{
            int partition=figureOutPartition(-1);
            System.out.println("In orderwrapper, received request to create order for user"+userId+", calling partition "+partition);
             HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/orders/create/"+userId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In orderwrapper, exception while requesting create order for user"+userId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }

    }

    //needs partitioning
    @DeleteMapping("/remove/{orderId}")
    public ResponseEntity remove(@PathVariable(name="orderId") int orderId) {
        try{
            int partition=figureOutPartition(orderId);
            System.out.println("In orderwrapper, received request to remove order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/orders/remove/"+orderId))
                    .DELETE()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
            System.out.println("In orderwrapper, exception while requesting remove order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    //needs partitioning
    @GetMapping(path = "/find/{orderId}")
    public ResponseEntity find(@PathVariable(name="orderId") int orderId) {
        try {
            int partition=figureOutPartition(orderId);
            System.out.println("In orderwrapper, requesting find order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/orders/find/"+orderId))
                    .GET()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            System.out.println("In orderwrapper, exception while requesting remove order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    //needs partitioning
    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public ResponseEntity addItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        try{
            int partition=figureOutPartition(orderId);
            System.out.println("In orderwrapper, requesting additem "+itemId+" to order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/orders/addItem/"+orderId+"/"+itemId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
            System.out.println("In orderwrapper, exception while requesting additem "+itemId+" to order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    //needs partitioning
    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public ResponseEntity removeItem(@PathVariable(name="orderId") int orderId,
                           @PathVariable(name="itemId") int itemId) {
        try{
            int partition=figureOutPartition(orderId);
            System.out.println("In orderwrapper, requesting removeitem "+itemId+" from order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/orders/removeItem/"+orderId+"/"+itemId))
                    .DELETE()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
            System.out.println("In orderwrapper, exception while requesting removeitem "+itemId+" from order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    //needs partitioning
    @PostMapping(path = "/checkout/{orderId}")
    public ResponseEntity checkout(@PathVariable(name="orderId") int orderId) {
        try{
            int partition=figureOutPartition(orderId);
            System.out.println("In orderwrapper, requesting checkout for order "+orderId+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/orders/checkout/"+orderId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
            System.out.println("In orderwrapper, exception while requesting checkout for order "+orderId);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieve instances registered with eureka to figure out how many are running and where to send based on provided id
     * @param id id to split on partition, or -1 if we want to assign random
     * @return id of instance to receive request
     */
    int figureOutPartition(int id){
        if(id<0){
            Random r=new Random();
            return r.nextInt(partitions);
        }else
            return (id%partitions);
    }
}
