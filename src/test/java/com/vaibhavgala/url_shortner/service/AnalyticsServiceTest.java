package com.vaibhavgala.url_shortner.service;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.service.enrichment.GeoIPService;
import com.vaibhavgala.url_shortner.service.events.ClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String DESKTOP_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String MOBILE_IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    @Mock
    private GeoIPService geoIpService;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("Builds a fully enriched analytics entity from a click event")
    void buildAnalyticsEntity_mapsFieldsAndEnriches() {
        when(geoIpService.getCountryAndCity("9.9.9.9")).thenReturn(new String[]{"India", "Mumbai"});
        ClickEvent event = new ClickEvent("abc", "9.9.9.9", DESKTOP_CHROME_UA, "https://referrer.com",
                LocalDateTime.of(2026, 1, 1, 12, 0));

        UrlClickAnalytics entity = analyticsService.buildAnalyticsEntity(event);

        assertNotNull(entity);
        assertEquals("abc", entity.getShortCode());
        assertEquals("9.9.9.9", entity.getIpAddress());
        assertEquals("India", entity.getCountry());
        assertEquals("Mumbai", entity.getCity());
        assertEquals("Chrome", entity.getBrowser());
        assertEquals("Windows", entity.getOs());
        assertEquals("Desktop", entity.getDeviceType());
    }

    @Test
    @DisplayName("Null event or event without a short code yields null")
    void buildAnalyticsEntity_nullEvent_returnsNull() {
        assertNull(analyticsService.buildAnalyticsEntity(null));
        assertNull(analyticsService.buildAnalyticsEntity(new ClickEvent(null, null, null, null, null)));
    }

    @Test
    @DisplayName("Blank user agent skips UA parsing but geolocation still runs")
    void buildAnalyticsEntity_blankUserAgent_stillGeocodes() {
        when(geoIpService.getCountryAndCity("1.1.1.1")).thenReturn(new String[]{"USA", null});
        ClickEvent event = new ClickEvent("abc", "1.1.1.1", "", null, LocalDateTime.now());

        UrlClickAnalytics entity = analyticsService.buildAnalyticsEntity(event);

        assertNull(entity.getBrowser());
        assertNull(entity.getOs());
        assertNull(entity.getDeviceType());
        assertEquals("USA", entity.getCountry());
        assertNull(entity.getCity());
    }
}