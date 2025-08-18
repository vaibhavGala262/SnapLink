package com.vaibhavgala.url_shortner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class KafkaClickConsumer {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UrlMappingRepository urlRepository;

    @Transactional
    @KafkaListener(topics = "click-events", groupId = "click-tracking-group", concurrency = "10")
    public void consumeClickEvents(List<String> clickEvents) {
        System.out.println("📦 Processing batch of " + clickEvents.size() + " click events");

        for (String clickEventJson : clickEvents) {
            try {
                // Process detailed analytics
                analyticsService.processClickEvent(clickEventJson);

                //  updating  simple click count for existing functionality
                Map<String, Object> event = new ObjectMapper().readValue(clickEventJson, Map.class);
                String shortCode = (String) event.get("shortCode");
                urlRepository.incrementClickCount(shortCode);

            } catch (Exception e) {
                System.err.println("❌ Event processing error: " + e.getMessage());
            }
        }

        System.out.println("✅ Processed " + clickEvents.size() + " analytics events");
    }
}
