package com.vaibhavgala.url_shortner.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.service.AnalyticsService;
import com.vaibhavgala.url_shortner.service.events.ClickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "app.features.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaClickConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickConsumer.class);
    private final AtomicLong totalBatches = new AtomicLong(0);

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UrlClickAnalyticsRepository analyticsRepository;

    @Autowired
    private UrlMappingRepository urlRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "click-events", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void processBatchClickEvents(
            List<String> clickEventsBatch,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            Acknowledgment acknowledgment
    ) {
        long batchNumber = totalBatches.incrementAndGet();
        Set<Integer> uniquePartitions = new HashSet<>(partitions);
        log.info("Processing batch #{}: {} events from partitions {}",
                batchNumber, clickEventsBatch.size(), uniquePartitions);

        List<UrlClickAnalytics> validAnalytics = new ArrayList<>();
        Map<String, Integer> clickCountUpdates = new HashMap<>();
        int parseErrors = 0;

        for (String eventJson : clickEventsBatch) {
            try {
                ClickEvent event = objectMapper.readValue(eventJson, ClickEvent.class);
                UrlClickAnalytics analytics = analyticsService.buildAnalyticsEntity(event);
                if (analytics != null && analytics.getShortCode() != null) {
                    validAnalytics.add(analytics);
                    clickCountUpdates.merge(analytics.getShortCode(), 1, Integer::sum);
                }
            } catch (Exception e) {
                parseErrors++;
                log.warn("Failed to parse event in batch #{}: {}", batchNumber, e.getMessage());
            }
        }

        if (!validAnalytics.isEmpty()) {
            analyticsRepository.saveAll(validAnalytics);
        }

        for (Map.Entry<String, Integer> entry : clickCountUpdates.entrySet()) {
            urlRepository.incrementClickCountBy(entry.getKey(), entry.getValue());
        }

        if (parseErrors > 0) {
            log.warn("Batch #{}: {} parse errors out of {} events", batchNumber, parseErrors, clickEventsBatch.size());
        }

        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}