package com.vaibhavgala.url_shortner.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.vaibhavgala.url_shortner.service.events.EventProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "app.features.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaClickProducer implements EventProducer {

    private static final String TOPIC = "click-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendClickEvent(String shortCode, String ipAddress, String userAgent, String referer) {
        // Create JSON event
        String clickEvent = String.format(
                "{\"shortCode\":\"%s\",\"ipAddress\":\"%s\",\"userAgent\":\"%s\",\"referer\":\"%s\",\"timestamp\":\"%s\"}",
                shortCode, ipAddress, userAgent, referer, LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC, clickEvent);
        System.out.println("🚀 KAFKA SENT: Click event for " + shortCode);
    }
}
