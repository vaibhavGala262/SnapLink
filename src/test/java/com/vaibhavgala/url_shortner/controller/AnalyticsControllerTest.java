package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlClickAnalyticsRepository analyticsRepository;

    @Test
    @DisplayName("GET /api/analytics/{code} returns totals, capped recent clicks and breakdowns")
    void getAnalytics_returnsAllSections() throws Exception {
        List<Object[]> countries = new ArrayList<>();
        countries.add(new Object[]{"India", 8L});
        countries.add(new Object[]{"USA", 7L});
        List<Object[]> devices = new ArrayList<>();
        devices.add(new Object[]{"Desktop", 10L});
        devices.add(new Object[]{"Mobile", 5L});
        List<Object[]> hours = new ArrayList<>();
        hours.add(new Object[]{8, 4L});
        hours.add(new Object[]{21, 11L});
        List<Object[]> referrers = new ArrayList<>();
        referrers.add(new Object[]{"https://reddit.com", 6L});

        when(analyticsRepository.findByShortCodeOrderByTimestampDesc("abc")).thenReturn(clicks(15));
        when(analyticsRepository.findClicksByCountry("abc")).thenReturn(countries);
        when(analyticsRepository.findClicksByDeviceType("abc")).thenReturn(devices);
        when(analyticsRepository.findClicksByHour(eq("abc"), any(LocalDateTime.class))).thenReturn(hours);
        when(analyticsRepository.findTopReferrers("abc")).thenReturn(referrers);

        mockMvc.perform(get("/api/analytics/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(15))
                .andExpect(jsonPath("$.recentClicks.length()").value(10))
                .andExpect(jsonPath("$.clicksByCountry[0][0]").value("India"))
                .andExpect(jsonPath("$.clicksByDevice[0][1]").value(10))
                .andExpect(jsonPath("$.clicksByHour[1][0]").value(21))
                .andExpect(jsonPath("$.topReferrers[0][0]").value("https://reddit.com"));
    }

    @Test
    @DisplayName("GET /api/analytics/{code} returns zeros and empty sections when there are no clicks")
    void getAnalytics_noClicks_returnsEmptyShape() throws Exception {
        List<Object[]> nothing = List.of();
        when(analyticsRepository.findByShortCodeOrderByTimestampDesc("xyz")).thenReturn(List.of());
        when(analyticsRepository.findClicksByCountry("xyz")).thenReturn(nothing);
        when(analyticsRepository.findClicksByDeviceType("xyz")).thenReturn(nothing);
        when(analyticsRepository.findClicksByHour(eq("xyz"), any(LocalDateTime.class))).thenReturn(nothing);
        when(analyticsRepository.findTopReferrers("xyz")).thenReturn(nothing);

        mockMvc.perform(get("/api/analytics/xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.recentClicks.length()").value(0))
                .andExpect(jsonPath("$.clicksByCountry.length()").value(0));
    }

    @Test
    @DisplayName("Hourly breakdown is computed over the trailing 24 hours")
    void getAnalytics_hourlyWindowCoversLast24Hours() throws Exception {
        when(analyticsRepository.findByShortCodeOrderByTimestampDesc("abc")).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/abc"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(analyticsRepository).findClicksByHour(eq("abc"), captor.capture());

        LocalDateTime since = captor.getValue();
        assertTrue(since.isAfter(LocalDateTime.now().minusHours(24).minusMinutes(1)));
        assertTrue(since.isBefore(LocalDateTime.now().minusHours(24).plusMinutes(1)));
    }

    private List<UrlClickAnalytics> clicks(int count) {
        List<UrlClickAnalytics> clicks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UrlClickAnalytics click = new UrlClickAnalytics();
            click.setId((long) i);
            click.setShortCode("abc");
            clicks.add(click);
        }
        return clicks;
    }
}