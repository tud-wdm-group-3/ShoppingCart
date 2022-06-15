package com.wsdm.payment;

import org.springframework.beans.factory.annotation.Value;

public class Environment {

    public static int numStockInstances = 2;

    public static int numPaymentInstances = 2;

    public static int numOrderInstances = 2;

    @Value("${PARTITION_ID:0}")
    public static int myPaymentInstanceId;
}
