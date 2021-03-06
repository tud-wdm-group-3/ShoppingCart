package com.wsdm.stock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.env.Environment;

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

    private Integer amount;
    private Double price;

    /**
     * Information on how much stock.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name="name")
    @Column(name="value")
    @CollectionTable(name="orderIdToItemsProcessed")
    private Map<Integer, Integer> orderToItemsProcessed = new HashMap<>();

    public Stock(double price){
        this.amount = 0;
        this.price = price;
    }

    public int getItemId(Environment env) {
        int numInstances = Integer.parseInt(env.getProperty("NUMSTOCK"));
        int instanceId = Integer.parseInt(env.getProperty("PARTITION"));
        return this.getLocalId() * numInstances + instanceId;
    }

    public static int getLocalId(int itemId, Environment env) {
        int numInstances = Integer.parseInt(env.getProperty("NUMSTOCK"));
        int instanceId = Integer.parseInt(env.getProperty("PARTITION"));
        return (itemId - instanceId) / numInstances;
    }
}
