package com.wsdm.payment.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Partitioner {
    public static int getPartition(int id, int numInstances) {
        return id % numInstances;
    }

    public static Map<Integer, List<Integer>> getPartition(List<Integer> ids, int numInstances) {
        Map<Integer, List<Integer>> partitionToIds = new HashMap<>();
        for (int id : ids) {
            int partition = getPartition(id, numInstances);
            List<Integer> curPartition = partitionToIds.getOrDefault(partition, new ArrayList<>());
            curPartition.add(id);
            partitionToIds.put(partition, curPartition);
        }
        return partitionToIds;
    }
}
