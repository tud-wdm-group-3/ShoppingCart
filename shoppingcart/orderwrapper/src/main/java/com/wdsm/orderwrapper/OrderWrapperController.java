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


    //doesn't need partitioning
    @PostMapping(value="/create/{userId}")
    public ResponseEntity create(@PathVariable(name="userId") int userId){
        try{
             HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(-1)+":8080/orders/create/"+userId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }

    }

    //needs partitioning
    @DeleteMapping("/remove/{orderId}")
    public void remove(@PathVariable(name="orderId") int orderId) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(orderId)+":8080/orders/remove/"+orderId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
        }catch (Exception ex){
            System.out.println("Exception when calling partition:"+ ex.getMessage());
        }
    }
    //needs partitioning
    @GetMapping(path = "/find/{orderId}")
    public ResponseEntity find(@PathVariable(name="orderId") int orderId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(orderId)+":8080/orders/find/"+orderId))
                    .GET()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    //needs partitioning
    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public ResponseEntity addItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(orderId)+":8080/orders/addItem/"+orderId+"/"+itemId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }
    }
    //needs partitioning
    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public ResponseEntity removeItem(@PathVariable(name="orderId") int orderId,
                           @PathVariable(name="itemId") int itemId) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(orderId)+":8080/orders/removeItem/"+orderId+"/"+itemId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }
    }
    //needs partitioning
    @PostMapping(path = "/checkout/{orderId}")
    public ResponseEntity checkout(@PathVariable(name="orderId") int orderId) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(orderId)+":8080/orders/checkout/"+orderId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        }catch (Exception ex){
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
