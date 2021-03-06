package com.wsdm.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.env.Environment;

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

    private Double credit = 0.0;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Integer> processedPaymentKeys = new HashSet<>();

    /**
     * Information on how much is paid.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name="name")
    @Column(name="value")
    @CollectionTable(name="orderIdToPaidAmount")
    private Map<Integer, Double> orderIdToPaidAmount = new HashMap<>();

    public int getUserId(Environment env) {
        int numInstances = Integer.parseInt(env.getProperty("NUMPAYMENT"));
        int instanceId = Integer.parseInt(env.getProperty("PARTITION"));
        return this.getLocalId() * numInstances + instanceId;
    }

    public static int getLocalId(int userId, Environment env) {
        int numInstances = Integer.parseInt(env.getProperty("NUMPAYMENT"));
        int instanceId = Integer.parseInt(env.getProperty("PARTITION"));
        return (userId - instanceId) / numInstances;
    }
}
