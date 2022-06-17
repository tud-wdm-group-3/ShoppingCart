package com.wsdm.stock.utils;

import java.net.InetAddress;

public abstract class NameUtils {
    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return "";
    }
}
