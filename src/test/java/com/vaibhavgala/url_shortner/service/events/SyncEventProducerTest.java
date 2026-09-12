package com.vaibhavgala.url_shortner.service.events;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncEventProducerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private UrlClickAnalyticsRepository analyticsRepository;

    @Mock
    private UrlMappingRepository urlRepository;

    @InjectMocks
    private SyncEventProducer producer;

    @Test
    @DisplayName("Persists the analytics row and increments the click counter")
    void sendClickEvent_savesAnalyticsAndIncrementsCount() {
        UrlClickAnalytics analytics = new UrlClickAnalytics();
        analytics.setShortCode("abc");
        when(analyticsService.buildAnalyticsEntity(any(ClickEvent.class))).thenReturn(analytics);

        producer.sendClickEvent("abc", "1.2.3.4", "UA", null);

        verify(analyticsRepository).save(analytics);
        verify(urlRepository).incrementClickCountBy("abc", 1);
    }

    @Test
    @DisplayName("Skips the database entirely when analytics cannot be built")
    void sendClickEvent_nullEntity_skipsDatabase() {
        when(analyticsService.buildAnalyticsEntity(any(ClickEvent.class))).thenReturn(null);

        producer.sendClickEvent("abc", "1.2.3.4", null, null);

        verifyNoInteractions(analyticsRepository, urlRepository);
    }

    @Test
    @DisplayName("Database failure is logged and swallowed, never propagated to the caller")
    void sendClickEvent_repositoryFailure_isSwallowed() {
        UrlClickAnalytics analytics = new UrlClickAnalytics();
        analytics.setShortCode("abc");
        when(analyticsService.buildAnalyticsEntity(any(ClickEvent.class))).thenReturn(analytics);
        doThrow(new RuntimeException("DB down")).when(analyticsRepository).save(any(UrlClickAnalytics.class));

        assertDoesNotThrow(() -> producer.sendClickEvent("abc", "1.2.3.4", null, null));
    }
}