package com.wsdm.order;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.env.Environment;

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
    private Set<Integer> items;
    private int userId;
    private double totalCost;
    private boolean paid;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Integer> processedPaymentKeys;

    private boolean inCheckout;
    private boolean checkedOut;
    private String replicaHandlingCheckout;

    public Order(int userId)
    {
        this.items = new HashSet<>();
        this.userId = userId;
        this.totalCost = 0.0;
        this.paid = false;
        this.inCheckout = false;
        this.processedPaymentKeys = new HashSet<>();
        this.replicaHandlingCheckout = "";
    }

    public int getOrderId(Environment env) {
        int numOrderInstances = Integer.parseInt(env.getProperty("NUMORDER"));
        int instanceId = Integer.parseInt(env.getProperty("PARTITION"));
        return this.getLocalId() * numOrderInstances + instanceId;
    }

    public static int getLocalId(int orderId, Environment env) {
        int numOrderInstances = Integer.parseInt(env.getProperty("NUMORDER"));
        int instanceId = Integer.parseInt(env.getProperty("PARTITION"));
        return (orderId - instanceId) / numOrderInstances;
    }
}
