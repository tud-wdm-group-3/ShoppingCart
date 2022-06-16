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

    @ElementCollection
    private List<Integer> items;
    private int userId;
    private int totalCost;
    private boolean paid;

    private boolean inCheckout;
    private String replicaHandlingCheckout;

    public Order(int userId)
    {
        items = new ArrayList<>();
        userId = userId;
        totalCost = 0;
        paid = false;
        inCheckout = false;
        replicaHandlingCheckout = "";
    }
}
