package com.vaibhavgala.url_shortner.service;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "app.features.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaClickConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickConsumer.class);
    private final AtomicLong processedEvents = new AtomicLong(0);
    private final AtomicLong totalBatches = new AtomicLong(0);

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UrlClickAnalyticsRepository analyticsRepository;

    @Autowired
    private UrlMappingRepository urlRepository;

    /**
     * TRUE BATCH PROCESSING - processes entire batches at once
     * Uses the new group ID to start fresh
     */
    @KafkaListener(
            topics = "click-events",
            groupId = "click-tracking-group-v4",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void processBatchClickEvents(
            List<String> clickEventsBatch,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment
    ) {
        long batchStartTime = System.currentTimeMillis();
        long batchNumber = totalBatches.incrementAndGet();

        // Get unique partitions for logging
        Set<Integer> uniquePartitions = new HashSet<>(partitions);

        log.info("🚀 BATCH #{}: Processing {} events from partitions {}",
                batchNumber, clickEventsBatch.size(), uniquePartitions);

        try {
            // Step 1: Parse all events in the batch
            List<UrlClickAnalytics> validAnalytics = new ArrayList<>();
            Map<String, Integer> clickCountUpdates = new HashMap<>();
            int parseErrors = 0;

            for (String eventJson : clickEventsBatch) {
                try {
                    UrlClickAnalytics analytics = analyticsService.buildAnalyticsEntity(eventJson);

                    if (analytics != null && analytics.getShortCode() != null) {
                        validAnalytics.add(analytics);

                        // Aggregate click counts per short code
                        String shortCode = analytics.getShortCode();
                        clickCountUpdates.merge(shortCode, 1, Integer::sum);
                    }
                } catch (Exception e) {
                    parseErrors++;
                    log.debug("Failed to parse event in batch #{}: {} - Error: {}",
                            batchNumber, eventJson, e.getMessage());
                }
            }

            // Step 2: SINGLE batch insert for analytics
            if (!validAnalytics.isEmpty()) {
                long insertStart = System.currentTimeMillis();
                analyticsRepository.saveAll(validAnalytics);
                long insertTime = System.currentTimeMillis() - insertStart;

                log.info("✅ BATCH #{}: Saved {} analytics records in {}ms",
                        batchNumber, validAnalytics.size(), insertTime);
            }

            // Step 3: Batch update click counts (one query per short code)
            if (!clickCountUpdates.isEmpty()) {
                long updateStart = System.currentTimeMillis();
                int updateCount = 0;

                for (Map.Entry<String, Integer> entry : clickCountUpdates.entrySet()) {
                    try {
                        urlRepository.incrementClickCountBy(entry.getKey(), entry.getValue());
                        updateCount++;
                    } catch (Exception e) {
                        log.warn("BATCH #{}: Failed to update click count for {}: {}",
                                batchNumber, entry.getKey(), e.getMessage());
                    }
                }

                long updateTime = System.currentTimeMillis() - updateStart;
                log.info("✅ BATCH #{}: Updated {} short codes in {}ms",
                        batchNumber, updateCount, updateTime);
            }

            // Step 4: Performance metrics
            long totalProcessingTime = System.currentTimeMillis() - batchStartTime;
            double eventsPerSecond = clickEventsBatch.size() / (totalProcessingTime / 1000.0);
            long totalProcessed = processedEvents.addAndGet(validAnalytics.size());

            log.info("🎯 BATCH #{} COMPLETED: {} events processed in {}ms ({:.1f} events/sec) | Total processed: {}",
                    batchNumber, clickEventsBatch.size(), totalProcessingTime, eventsPerSecond, totalProcessed);

            if (parseErrors > 0) {
                log.warn("⚠️ BATCH #{}: {} parse errors out of {} events ({:.1f}% error rate)",
                        batchNumber, parseErrors, clickEventsBatch.size(),
                        (parseErrors * 100.0) / clickEventsBatch.size());
            }

            // Step 5: Manual acknowledgment (optional - auto-commit is enabled)
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }

        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR in BATCH #{} with {} events: {}",
                    batchNumber, clickEventsBatch.size(), e.getMessage(), e);

            // Don't rethrow - let Kafka auto-commit and move on
            // This prevents infinite retry loops
            log.warn("⚠️ BATCH #{}: Skipping problematic batch to prevent retry storm", batchNumber);
        }
    }

    /**
     * Health check method for monitoring
     */
    public Map<String, Object> getConsumerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBatchesProcessed", totalBatches.get());
        stats.put("totalEventsProcessed", processedEvents.get());
        stats.put("averageEventsPerBatch",
                totalBatches.get() > 0 ? (double) processedEvents.get() / totalBatches.get() : 0);
        return stats;
    }
}