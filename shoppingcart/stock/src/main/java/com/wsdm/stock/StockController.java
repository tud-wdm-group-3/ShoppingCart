package com.wsdm.stock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

import java.util.*;

@RestController
@RequestMapping("stock")
public record StockController(StockService stockService) {

    @GetMapping(path = "/dump")
    public List<Stock> dump() {
        return stockService.dump();
    }

    @GetMapping(path="/find/{item_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object findItem(@PathVariable(name="item_id") int item_id){
        // System.out.println("Finding item " + item_id);
        Optional<Stock> stock = stockService.findItem(item_id);
        if (stock.isPresent()){
            return Map.of("stock", stock.get().getAmount(), "price", stock.get().getPrice());
        }
        ResponseEntity response = ResponseEntity.notFound().build();
        return response;
    }

    @PostMapping(path="/subtract/{item_id}/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity subtractStock(@PathVariable(name="item_id") int item_id,
                         @PathVariable(name="amount") int amount) {
        // System.out.println("subtracting stock to " + item_id + " with amount " + amount);
        if (amount <= 0)
            return ResponseEntity.badRequest().build();

        if (stockService.subtractStock(item_id, amount))
            return ResponseEntity.ok().build();
        else
            return ResponseEntity.badRequest().build();
    }

    @PostMapping(path="/add/{item_id}/{amount}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity addStock(@PathVariable(name="item_id") int item_id,
                                           @PathVariable(name="amount") int amount) {
        // System.out.println("adding stock to " + item_id + " with amount " + amount);
        if (amount <= 0)
            return ResponseEntity.badRequest().build();

        if (stockService.addStock(item_id, amount))
            return ResponseEntity.ok().build();
        else
            return ResponseEntity.notFound().build();
    }

    @PostMapping(path="/item/create/{price}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object addItem(@PathVariable(name="price") double price) {
        if (price <= 0)
            return ResponseEntity.badRequest().build();

        int itemId = stockService.createItem(price);
        // System.out.println("Adding item on created " + itemId);
        return Map.of("item_id", itemId);
    }



}
