package com.wsdm.order.utils;

import java.util.HashMap;
import java.util.Map;

public class ItemPrices {
    /**
     * Map itemdId to price.
     */
    private static Map<Integer, Integer> itemPrices = new HashMap<>();

    public static void addItemPrice(int itemId, int price) {
        itemPrices.put(itemId, price);
    }

    public static boolean itemExists(int itemId) {
        return itemPrices.containsKey(itemId);
    }

    public static int getItemPrice(int itemId) {
        return itemPrices.get(itemId);
    }
}
