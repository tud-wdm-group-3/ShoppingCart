package com.wsdm.stock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("stock")
public record StockController(StockService stockService) {

    @GetMapping(path="/find/{item_id}")
    public HashMap<String, String> findStock(@PathVariable(name="item_id") int item_id){
        Optional<Stock> stock = stockService.findStock(item_id);
        HashMap<String,String> res =new HashMap<>();
        if (stock.isPresent()){
            res.put("amount", stock.get().getAmount().toString());
            res.put("price", stock.get().getPrice().toString());
            return res;
        }
        res.put("error", "Item not found.");
        return res;
    }

    @PostMapping(path="/subtract/{item_id}/{amount}")
    public ResponseEntity subtractStock(@PathVariable(name="item_id") int item_id,
                         @PathVariable(name="amount") int amount) {
        if (stockService.subtractStock(item_id, amount))
            return ResponseEntity.ok().build();
        else
            return ResponseEntity.internalServerError().build();
    }

    @PostMapping(path="/add/{item_id}/{amount}")
    public ResponseEntity addStock(@PathVariable(name="item_id") int item_id,
                                           @PathVariable(name="amount") int amount) {
        if (stockService.addStock(item_id, amount))
            return ResponseEntity.ok().build();
        else
            return ResponseEntity.internalServerError().build();
    }

    @PostMapping(path="/item/create/{price}")
    public HashMap<String, String> addItem(@PathVariable(name="price") double price) {
        Stock stock = stockService.addItem(price);
        HashMap<String, String> res = new HashMap<String, String>();
        res.put("item_id", stock.getItem_id().toString());

        return res;
    }



}