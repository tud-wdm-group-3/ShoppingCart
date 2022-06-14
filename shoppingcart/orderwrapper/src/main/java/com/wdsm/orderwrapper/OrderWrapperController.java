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
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Random;

@RestController
@RequiredArgsConstructor
@Import(FeignClientProperties.FeignClientConfiguration.class)
public class OrderWrapperController {

    private final OrderWrapperService orderWrapperService=null;
    @Autowired
    private EurekaClient eurekaClient;
    private final OrderClient orderClient;
    private static final String baseUri="http://";

    @Autowired
    public OrderWrapperController(Decoder decoder, Encoder encoder){
        orderClient= Feign.builder().encoder(encoder).decoder(decoder).target(Target.EmptyTarget.create(OrderClient.class));
    }

    //doesn't need partitioning
    @RequestMapping(value="/create/{userId}")
    public ResponseEntity<String> create(@PathVariable(name="userId") int userId){
        //String res=orderWrapperService.createOrder(userId,figureOutPartition(-1));
        //return ResponseEntity.ok().body(res);
        try {
            return ResponseEntity.ok().body(orderClient.create(userId, new URI(baseUri + "order-" + figureOutPartition(-1) + "orders/create/{" + userId + "}")));

        }catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    //needs partitioning
    @DeleteMapping("/remove/{orderId}")
    public void remove(@PathVariable(name="orderId") int orderId) {
        orderWrapperService.removeOrder(orderId,figureOutPartition(orderId));
    }
    //needs partitioning
    @GetMapping(path = "/find/{orderId}")
    public Order find(@PathVariable(name="orderId") int orderId) {
        try {
            return orderClient.find(orderId, new URI(baseUri + "order-" + figureOutPartition(orderId) + "orders/find/{" + orderId + "}"));
        } catch (Exception ex) {
            return null;
        }
    }

    //needs partitioning
    @PostMapping(path = "/addItem/{orderId}/{itemId}")
    public void addItem(@PathVariable(name="orderId") int orderId,
                        @PathVariable(name="itemId") int itemId) {
        orderWrapperService.addItemInOrder(orderId,itemId,figureOutPartition(orderId));
    }
    //needs partitioning
    @DeleteMapping(path = "/removeItem/{orderId}/{itemId}")
    public void removeItem(@PathVariable(name="orderId") int orderId,
                           @PathVariable(name="itemId") int itemId) {
        orderWrapperService.removeItemFromOrder(orderId,itemId,figureOutPartition(orderId));
    }
    //needs partitioning
    @PostMapping(path = "/checkout/{orderId}")
    public ResponseEntity checkout(@PathVariable(name="orderId") int orderId) {
        return orderWrapperService.checkoutOrder(orderId,figureOutPartition(orderId));
    }

    /**
     * Retrieve instances registered with eureka to figure out how many are running and where to send based on provided id
     * @param id id to split on partition, or -1 if we want to assign random
     * @return id of instance to receive request
     */
    int figureOutPartition(int id){
        Applications apps=eurekaClient.getApplications();
        int sum=1;
        for(Application app:apps.getRegisteredApplications()){
            String[] name=app.getName().split("-");
            if(name[0].equalsIgnoreCase("order"))
                sum++;
        }
        if(id<0){
            Random r=new Random();
            return r.nextInt(sum);
        }else
            return (id%sum);
    }
}
