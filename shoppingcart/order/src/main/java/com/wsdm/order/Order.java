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
    private int order_id;
    private boolean paid;
    @ElementCollection
    private List<Integer> items;
    private int user_id;
    private int total_cost;

    public Order(int user_id)
    {
        paid=false;
        items=new ArrayList<>();
        user_id=user_id;
        total_cost=0;
    }
}
