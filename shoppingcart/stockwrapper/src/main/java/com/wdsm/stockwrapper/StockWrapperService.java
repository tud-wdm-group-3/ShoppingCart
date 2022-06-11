package com.wdsm.stockwrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockWrapperService {

    private final StockClient stockClient;

    public Object findStockItem(int itemId,int partitionId){
        return stockClient.findStockitem(itemId, partitionId);
    }

    public ResponseEntity removeStockItemQuantity(int itemId, int amount, int partitionId){
        return stockClient.removeStockItemQuantity(itemId, amount, partitionId);
    }

    public ResponseEntity addStockItemQuantity(int itemId,int amount,int partitionId){
        return addStockItemQuantity(itemId, amount, partitionId);
    }

    public Object addStockItem(int price,int partitionId){
        return addStockItem(price,partitionId);
    }
}
