package com.wdsm.stockwrapper;

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
public class StockWrapperController {

    private static final String baseUri="http://stock-";
    @Value("${partitions}")
    private int partitions;

    @GetMapping(path="/find/{item_id}")
    public ResponseEntity findItem(@PathVariable(name="item_id") int item_id){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(item_id)+":8080/stock/find/"+item_id))
                    .GET()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="/subtract/{item_id}/{amount}")
    public ResponseEntity subtractStock(@PathVariable(name="item_id") int item_id,
                                        @PathVariable(name="amount") int amount){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(item_id)+":8080/stock/subtract/"+item_id+"/"+amount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="/add/{item_id}/{amount}")
    public ResponseEntity addStock(@PathVariable(name="item_id") int item_id,
                                   @PathVariable(name="amount") int amount){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(item_id)+":8080/stock/add/"+item_id+"/"+amount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="/item/create/{price}")
    public ResponseEntity addItem(@PathVariable(name="price") int price){
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(-1)+":8080/stock/item/create/"+price))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
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
