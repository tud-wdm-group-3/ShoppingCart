package com.wsdm.payment.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExistingOrders {
    /**
     * A log of current order statuses.
     */
    private static Map<Integer, Set<Integer>> existingOrders = new HashMap<>();

    public static void addOrder(int userId, int orderId) {
        if (!existingOrders.containsKey(userId)) {
            existingOrders.put(userId, new HashSet<>());
        }

        existingOrders.get(userId).add(orderId);
        System.out.println(existingOrders);
    }


    public static void removeOrder(int userId, int orderId) {
        assert(existingOrders.containsKey(userId));
        assert(existingOrders.get(userId).contains(orderId));
        existingOrders.get(userId).remove(orderId);

    }

    public static boolean orderExists(int userId, int orderId) {
        if (existingOrders.containsKey(userId)) {
            return existingOrders.get(userId).contains(orderId);
        }
        return false;
    }

}
