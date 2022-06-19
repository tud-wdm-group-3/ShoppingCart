package com.wsdm.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Table(name = "Stock")
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer localId;

    private Integer itemId;
    private Integer amount;
    private Integer price;

    enum StockBroadcasted {
        NO,
        YES
    }
    private StockBroadcasted stockBroadcasted;

    /**
     * Information on how much stock.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name="name")
    @Column(name="value")
    @CollectionTable(name="orderIdToItemsProcessed")
    private Map<Integer, Integer> orderToItemsProcessed = new HashMap<>();

    public Stock(int price){
        this.amount = 0;
        this.price = price;
        this.stockBroadcasted = StockBroadcasted.NO;
    }
}
