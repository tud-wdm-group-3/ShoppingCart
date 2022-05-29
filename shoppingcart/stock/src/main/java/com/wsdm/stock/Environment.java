package com.wsdm.stock;

import org.springframework.beans.factory.annotation.Value;

public class Environment {

    @Value("${numStockInstances}")
    public static int numStockInstances;

    @Value("${numPaymentInstances}")
    public static int numPaymentInstances;

    @Value("${numOrderInstances}")
    public static int numOrderInstances;

    @Value("${myOrderInstanceId}")
    public static int myStockInstanceId;
}
