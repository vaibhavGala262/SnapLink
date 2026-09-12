package com.vaibhavgala.url_shortner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.service.events.ClickEvent;
import com.vaibhavgala.url_shortner.service.events.EventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "app.features.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaClickProducer implements EventProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickProducer.class);
    private static final String TOPIC = "click-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void sendClickEvent(String shortCode, String ipAddress, String userAgent, String referer) {
        try {
            ClickEvent event = new ClickEvent(shortCode, ipAddress, userAgent, referer, LocalDateTime.now());
            kafkaTemplate.send(TOPIC, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish click event for {}: {}", shortCode, e.getMessage(), e);
        }
    }
}