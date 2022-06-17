package com.wsdm.order;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @ElementCollection
    private Set<Integer> processedPaymentKeys;

    private boolean inCheckout;
    private String replicaHandlingCheckout;

    public Order(int userId)
    {
        items = new ArrayList<>();
        userId = userId;
        totalCost = 0;
        paid = false;
        inCheckout = false;
        processedPaymentKeys = new HashSet<>();
        replicaHandlingCheckout = "";
    }
}
