package com.vaibhavgala.url_shortner.service.events;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "app.features.kafka.enabled", havingValue = "false")
public class SyncEventProducer implements EventProducer {

    private static final Logger log = LoggerFactory.getLogger(SyncEventProducer.class);

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
            ClickEvent event = new ClickEvent(shortCode, ipAddress, userAgent, referer, LocalDateTime.now());
            UrlClickAnalytics analytics = analyticsService.buildAnalyticsEntity(event);
            if (analytics != null && analytics.getShortCode() != null) {
                analyticsRepository.save(analytics);
                urlRepository.incrementClickCountBy(shortCode, 1);
                log.info("Saved click event for {}", shortCode);
            }
        } catch (Exception e) {
            log.error("Failed to save click event for {}: {}", shortCode, e.getMessage(), e);
        }
    }
}