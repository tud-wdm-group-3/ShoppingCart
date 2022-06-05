package com.wsdm.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

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
    private Double price;

    public Stock(double price){
        amount=1;
        price = price;
    }
}
