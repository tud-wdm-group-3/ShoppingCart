package com.wsdm.stock.utils;

import org.springframework.core.env.Environment;

import java.util.*;

public class Partitioner {

    public enum Service {
        ORDER,
        PAYMENT,
        STOCK
    }

    public static int getPartition(int id, Service service, Environment env) {
        int numInstances = -1;
        switch (service) {
            case ORDER:
                numInstances = Integer.parseInt(env.getProperty("NUMORDER"));
                break;
            case STOCK:
                numInstances = Integer.parseInt(env.getProperty("NUMSTOCK"));
                break;
            case PAYMENT:
                numInstances = Integer.parseInt(env.getProperty("NUMPAYMENT"));
                break;
            default:
                throw new AssertionError("unknown service");
        }
        return id % numInstances;
    }

    public static Map<Integer, List<Integer>> getPartition(Collection<Integer> ids, Service service, Environment env) {
        Map<Integer, List<Integer>> partitionToIds = new HashMap<>();
        for (int id : ids) {
            int partition = getPartition(id, service, env);
            List<Integer> curPartition = partitionToIds.getOrDefault(partition, new ArrayList<>());
            curPartition.add(id);
            partitionToIds.put(partition, curPartition);
        }
        return partitionToIds;
    }
}