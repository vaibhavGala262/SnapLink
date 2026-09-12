package com.vaibhavgala.url_shortner.service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.service.AnalyticsService;
import com.vaibhavgala.url_shortner.service.events.ClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaClickConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private UrlClickAnalyticsRepository analyticsRepository;

    @Mock
    private UrlMappingRepository urlRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private KafkaClickConsumer consumer;

    @Test
    @DisplayName("Persists the whole batch, aggregates click counts per code and acknowledges")
    void processBatch_savesAll_aggregatesCounts_acknowledges() throws JsonProcessingException {
        when(objectMapper.readValue("e1", ClickEvent.class))
                .thenReturn(new ClickEvent("abc", "1.1.1.1", null, null, null));
        when(objectMapper.readValue("e2", ClickEvent.class))
                .thenReturn(new ClickEvent("abc", "2.2.2.2", null, null, null));
        when(analyticsService.buildAnalyticsEntity(any(ClickEvent.class)))
                .thenReturn(analytics("abc"), analytics("abc"));

        consumer.processBatchClickEvents(List.of("e1", "e2"), List.of(0, 0), acknowledgment);

        ArgumentCaptor<List<UrlClickAnalytics>> captor = ArgumentCaptor.forClass(List.class);
        verify(analyticsRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        verify(urlRepository).incrementClickCountBy("abc", 2);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Malformed events are skipped while valid ones are still persisted")
    void processBatch_parseError_skipsBadEvent_stillPersistsValid() throws JsonProcessingException {
        when(objectMapper.readValue("good1", ClickEvent.class))
                .thenReturn(new ClickEvent("abc", "1.1.1.1", null, null, null));
        when(objectMapper.readValue("good2", ClickEvent.class))
                .thenReturn(new ClickEvent("xyz", "2.2.2.2", null, null, null));
        when(objectMapper.readValue("bad-json", ClickEvent.class))
                .thenThrow(new RuntimeException("parse error"));
        when(analyticsService.buildAnalyticsEntity(any(ClickEvent.class)))
                .thenReturn(analytics("abc"), analytics("xyz"));

        consumer.processBatchClickEvents(List.of("good1", "bad-json", "good2"), List.of(0, 0), acknowledgment);

        verify(analyticsRepository).saveAll(anyList());
        verify(urlRepository).incrementClickCountBy("abc", 1);
        verify(urlRepository).incrementClickCountBy("xyz", 1);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Empty batches touch nothing and missing acknowledgments do not crash")
    void processBatch_emptyOrNullAck_doesNotCrash() {
        assertDoesNotThrow(() -> consumer.processBatchClickEvents(List.of(), List.of(), null));

        consumer.processBatchClickEvents(List.of(), List.of(), acknowledgment);

        verifyNoInteractions(analyticsRepository, urlRepository);
        verify(acknowledgment).acknowledge();
    }

    private UrlClickAnalytics analytics(String shortCode) {
        UrlClickAnalytics entity = new UrlClickAnalytics();
        entity.setShortCode(shortCode);
        return entity;
    }
}