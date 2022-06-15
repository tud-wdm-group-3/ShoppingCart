package com.wdsm.orderwrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
public class OrderWrapperApplication {
    public static void main(String[] args){
        SpringApplication.run(OrderWrapperApplication.class,args);
    }
}
