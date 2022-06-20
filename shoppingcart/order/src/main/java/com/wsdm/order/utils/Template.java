package com.wsdm.order.utils;

import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

public class Template {
    private static KafkaTemplate<Integer, Object> template;

    public static void addTemplate(KafkaTemplate<Integer, Object> kafkaTemplate) {
        template = kafkaTemplate;
    }

    public static void send(String topic, int partition, int key, Map<String, Object> data) {
        template.send(topic, partition, key, data);
    }
}
