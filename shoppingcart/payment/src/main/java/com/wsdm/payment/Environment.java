package com.wsdm.payment;

import org.springframework.beans.factory.annotation.Value;

public class Environment {

    public static int numStockInstances = 1;

    public static int numPaymentInstances = 1;

    public static int numOrderInstances = 1;

    public static int myPaymentInstanceId = 0;
}