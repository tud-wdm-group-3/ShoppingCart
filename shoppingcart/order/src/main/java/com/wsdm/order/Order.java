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

    @ElementCollection(fetch = FetchType.EAGER)
    private List<Integer> items;
    private int userId;
    private double totalCost;
    private boolean paid;

    enum OrderBroadcasted {
        NO,
        YES,
        PROCESSING_DELETION,
        DELETED
    }
    private OrderBroadcasted orderBroadcasted;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Integer> processedPaymentKeys;

    private boolean inCheckout;
    private boolean checkedOut;
    private String replicaHandlingCheckout;

    public Order(int userId)
    {
        this.items = new ArrayList<>();
        this.userId = userId;
        this.totalCost = 0.0;
        this.paid = false;
        this.orderBroadcasted = OrderBroadcasted.YES;
        this.inCheckout = false;
        this.processedPaymentKeys = new HashSet<>();
        this.replicaHandlingCheckout = "";
    }

    public int getOrderId(int numOrderInstances, int myOrderInstanceId) {
        return this.getLocalId() * numOrderInstances + myOrderInstanceId;
    }
}
