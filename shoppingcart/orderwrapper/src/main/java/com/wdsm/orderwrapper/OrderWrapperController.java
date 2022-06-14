package com.wdsm.orderwrapper;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.wsdm.order.Order;
import feign.Feign;
import feign.Target;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

@RestController
@RequiredArgsConstructor
//@Import(FeignClientProperties.FeignClientConfiguration.class)
public class OrderWrapperController {

    //private final OrderWrapperService orderWrapperService=null;
    //private EurekaClient eurekaClient;
    //private final OrderClient orderClient;
    private static final String baseUri="http://order-";
    @Value("${partitions}")
    private int partitions;


    //doesn't need partitioning
    @RequestMapping(value="/create/{userId}")
    public ResponseEntity<String> create(@PathVariable(name="userId") int userId){
        try{
             HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(-1)+"/orders/create/"+userId))
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
        //orderWrapperService.removeOrder(orderId,figureOutPartition(orderId));
    }
    //needs partitioning
    @GetMapping(path = "/find/{orderId}")
    public ResponseEntity<String> find(@PathVariable(name="orderId") int orderId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(baseUri+figureOutPartition(orderId)+"/orders/find/"+orderId))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response= HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    //needs partitioning
    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public void addItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        //orderWrapperService.addItemInOrder(orderId,itemId,figureOutPartition(orderId));
    }
    //needs partitioning
    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public void removeItem(@PathVariable(name="orderId") int orderId,
                           @PathVariable(name="itemId") int itemId) {
        //orderWrapperService.removeItemFromOrder(orderId,itemId,figureOutPartition(orderId));
    }
    //needs partitioning
    @PostMapping(path = "/checkout/{orderId}")
    public ResponseEntity checkout(@PathVariable(name="orderId") int orderId) {
        //return orderWrapperService.checkoutOrder(orderId,figureOutPartition(orderId));
        return  ResponseEntity.ok().build();
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
