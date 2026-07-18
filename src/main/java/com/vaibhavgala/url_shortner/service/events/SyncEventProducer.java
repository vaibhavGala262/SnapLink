package com.vaibhavgala.url_shortner.service.events;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "app.features.kafka.enabled", havingValue = "false")
public class SyncEventProducer implements EventProducer {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UrlClickAnalyticsRepository analyticsRepository;

    @Autowired
    private UrlMappingRepository urlRepository;

    @Override
    @Transactional
    public void sendClickEvent(String shortCode, String ipAddress, String userAgent, String referer) {
        try {
            // Create JSON event (reusing the same logic as Kafka for consistency)
            String clickEvent = String.format(
                    "{\"shortCode\":\"%s\",\"ipAddress\":\"%s\",\"userAgent\":\"%s\",\"referer\":\"%s\",\"timestamp\":\"%s\"}",
                    shortCode, ipAddress, userAgent, referer, LocalDateTime.now()
            );

            // Parse back to entity
            UrlClickAnalytics analytics = analyticsService.buildAnalyticsEntity(clickEvent);

            if (analytics != null && analytics.getShortCode() != null) {
                // Save analytics record
                analyticsRepository.save(analytics);
                
                // Increment click count
                urlRepository.incrementClickCountBy(shortCode, 1);
                
                System.out.println("💾 SYNC SAVED: Click event for " + shortCode);
            }
        } catch (Exception e) {
            System.err.println("❌ SYNC ERROR: Failed to save click event: " + e.getMessage());
        }
    }
}
