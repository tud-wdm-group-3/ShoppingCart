package com.wsdm.order.utils;

import java.util.HashMap;
import java.util.Map;

public class ItemPrices {
    /**
     * Map itemdId to price.
     */
    private static Map<Integer, Double> itemPrices = new HashMap<>();

    public static void addItemPrice(int itemId, double price) {
        itemPrices.put(itemId, price);
    }

    public static boolean itemExists(int itemId) {
        return itemPrices.containsKey(itemId);
    }

    public static double getItemPrice(int itemId) {
        return itemPrices.get(itemId);
    }
}
