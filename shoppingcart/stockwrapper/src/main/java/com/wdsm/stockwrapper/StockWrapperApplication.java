package com.wdsm.stockwrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class StockWrapperApplication {
    public static void main(String[] args){
        SpringApplication.run(StockWrapperApplication.class,args);
    }
}
