package com.wdsm.stockwrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

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

    @GetMapping(path="/")
    public ResponseEntity health(){
        return ResponseEntity.ok().build();
    }

    @GetMapping(path="/find/{item_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity findItem(@PathVariable(name="item_id") int item_id){
        try{
            int partition = figureOutPartition(item_id);
            System.out.println("In stockwrapper, requesting find item "+item_id+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/stock/find/"+item_id))
                    .GET()
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In stockwrapper, exception requesting find item "+item_id);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="/subtract/{item_id}/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity subtractStock(@PathVariable(name="item_id") int item_id,
                                        @PathVariable(name="amount") int amount){
        try{
            int partition=figureOutPartition(item_id);
            System.out.println("In stockwrapper, requesting subtract item "+item_id+" for amount "+amount+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/stock/subtract/"+item_id+"/"+amount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In stockwrapper, exception requesting subtract item "+item_id+" for amount "+amount);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="/add/{item_id}/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity addStock(@PathVariable(name="item_id") int item_id,
                                   @PathVariable(name="amount") int amount){
        try{
            int partition = figureOutPartition(item_id);
            System.out.println("In stockwrapper, requesting add item "+item_id+" with amount "+amount+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/stock/add/"+item_id+"/"+amount))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In stockwrapper, exception requesting add item "+item_id+" with amount "+amount);
            System.out.println(ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(path="/item/create/{price}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity addItem(@PathVariable(name="price") double price){
        try{
            int partition = figureOutPartition(-1);
            System.out.println("In stockwrapper, requesting create item with price "+price+", calling partition "+partition);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+partition+":8080/stock/item/create/"+price))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        }catch (Exception ex){
            System.out.println("In stockwrapper, exception requesting create item with price "+price);
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
