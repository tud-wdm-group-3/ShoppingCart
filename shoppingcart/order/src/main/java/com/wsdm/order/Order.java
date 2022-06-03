package com.wsdm.order;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int localId;

    private int orderId;
    private boolean paid;
    @ElementCollection
    private List<Integer> items;
    private int userId;
    private int totalCost;

    public Order(int userId)
    {
        paid=false;
        items=new ArrayList<>();
        userId=userId;
        totalCost=0;
    }
}
