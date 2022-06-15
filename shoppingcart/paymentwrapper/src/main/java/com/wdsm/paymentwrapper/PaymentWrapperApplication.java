package com.wdsm.paymentwrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class PaymentWrapperApplication {
    public static void main(String[] args){
        SpringApplication.run(PaymentWrapperApplication.class,args);
    }
}
