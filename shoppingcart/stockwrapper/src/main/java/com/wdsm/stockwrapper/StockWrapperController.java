package com.wdsm.stockwrapper;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequiredArgsConstructor
public class StockWrapperController {

    private final StockWrapperService stockWrapperService;
    @Autowired
    private EurekaClient eurekaClient;

    @GetMapping(path="/find/{item_id}")
    public Object findItem(@PathVariable(name="item_id") int item_id){
        return stockWrapperService.findStockItem(item_id,figureOutPartition(item_id));
    }

    @PostMapping(path="/subtract/{item_id}/{amount}")
    public ResponseEntity subtractStock(@PathVariable(name="item_id") int item_id,
                                        @PathVariable(name="amount") int amount){
        return stockWrapperService.removeStockItemQuantity(item_id,amount,figureOutPartition(item_id));
    }

    @PostMapping(path="/add/{item_id}/{amount}")
    public ResponseEntity addStock(@PathVariable(name="item_id") int item_id,
                                   @PathVariable(name="amount") int amount){
        return stockWrapperService.addStockItemQuantity(item_id,amount,figureOutPartition(item_id));
    }

    @PostMapping(path="/item/create/{price}")
    public Object addItem(@PathVariable(name="price") int price){
        return stockWrapperService.addStockItem(price,figureOutPartition(-1));
    }

    //see orderwrapper javadoc for relevant info
    int figureOutPartition(int id){
        Applications apps=eurekaClient.getApplications();
        int sum=0;
        for(Application app:apps.getRegisteredApplications()){
            String[] name=app.getName().split("-");
            if(name[0].equalsIgnoreCase("stock"))
                sum++;
        }
        if(id<0){
            Random r=new Random();
            return r.nextInt(sum);
        }else
            return (id%sum);
    }
}
