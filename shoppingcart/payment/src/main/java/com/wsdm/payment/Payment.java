package com.wsdm.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer localId;

    private Integer userId;
    private Integer credit;

    @ElementCollection
    private Set<Integer> processedPaymentKeys = new HashSet<>();

    /**
     * Information on how much is paid.
     */
    @ElementCollection
    @MapKeyColumn(name="name")
    @Column(name="value")
    @CollectionTable(name="orderIdToPaidAmount", joinColumns=@JoinColumn(name="id"))
    private Map<Integer, Integer> orderIdToPaidAmount = new HashMap<>();

}
