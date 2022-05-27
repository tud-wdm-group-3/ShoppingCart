package com.wsdm.payment;

import org.springframework.kafka.annotation.KafkaListener;

public class KafkaSubscriber {


    @KafkaListener(topics = "test", groupId = "what is this")
    public void consume(String message) {
        System.out.println(message);
    }
}
