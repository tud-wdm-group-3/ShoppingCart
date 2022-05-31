package com.wsdm.stock;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public record StockService(StockRepository stockRepository) {

    public Optional<Stock> findStock(int item_id) {
        Optional<Stock> res = stockRepository.findById(item_id);
        return res;
    }

    public List<Stock> findStocks(List<Integer> item_ids) {
        List<Stock> res = stockRepository.findAllById(item_ids);
        return res;
    }

    public boolean addStock(int item_id, int amount) {
        Optional<Stock> res = stockRepository.findById(item_id);
        if(res.isPresent()) {
            Stock stock = res.get();
            stock.setAmount(stock.getAmount()+amount);
            stockRepository.save(stock);
            return true;
        }
        return false;
    }

    public boolean subtractStock(int item_id, int amount) {
        Optional<Stock> res = stockRepository.findById(item_id);
        if(res.isPresent()) {
            Stock stock = res.get();
            if (stock.getAmount() >= amount) {
                stock.setAmount(stock.getAmount()-amount);
                stockRepository.save(stock);
                return true;
            }
        }
        return false;
    }

    public Stock addItem(double price) {
        Stock stock = new Stock(price);
        stock.setPrice(price);
        stockRepository.save(stock);

        return stock;
    }


}
