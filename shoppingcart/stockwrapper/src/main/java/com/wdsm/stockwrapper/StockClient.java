package com.wdsm.stockwrapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Component
public class StockClient {
    FeignClientBuilder feignClientBuilder;
    public StockClient(@Autowired ApplicationContext applicationContext){
        this.feignClientBuilder=new FeignClientBuilder(applicationContext);
    }

    interface CustomCall{
        @GetMapping(path="/find/{item_id}")
        Object findItem(@PathVariable(name="item_id") int item_id);

        @PostMapping(path="/subtract/{item_id}/{amount}")
        ResponseEntity subtractStock(@PathVariable(name="item_id") int item_id,
                                            @PathVariable(name="amount") int amount);

        @PostMapping(path="/add/{item_id}/{amount}")
        ResponseEntity addStock(@PathVariable(name="item_id") int item_id,
                                       @PathVariable(name="amount") int amount);

        @PostMapping(path="/item/create/{price}")
        Object addItem(@PathVariable(name="price") int price);
    }

    public Object findStockitem(int itemId,int partitionId)
    {
        return delegate(partitionId).findItem(itemId);
    }

    public ResponseEntity removeStockItemQuantity(int itemId,int amount,int partitionId){
        return delegate(partitionId).subtractStock(itemId,amount);
    }

    public ResponseEntity addStockItemQuantity(int itemId,int amount,int partitionId){
        return delegate(partitionId).addStock(itemId,amount);
    }

    public Object addStockItem(int price,int partitionId){
        return delegate(partitionId).addItem(price);
    }

    public CustomCall delegate(int partitionId){
        String serviceName="stock-"+partitionId;
        return this.feignClientBuilder.forType(CustomCall.class,serviceName).build();
    }
}
