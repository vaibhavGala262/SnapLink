package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private UrlClickAnalyticsRepository analyticsRepository;

    @GetMapping("/{shortCode}")
    public Map<String, Object> getAnalytics(@PathVariable String shortCode) {
        Map<String, Object> analytics = new HashMap<>();

        // Basic stats
        List<UrlClickAnalytics> clicks = analyticsRepository.findByShortCodeOrderByTimestampDesc(shortCode);
        analytics.put("totalClicks", clicks.size());
        analytics.put("recentClicks", clicks.subList(0, Math.min(10, clicks.size())));

        // Geographic breakdown
        analytics.put("clicksByCountry", analyticsRepository.findClicksByCountry(shortCode));

        // Device breakdown
        analytics.put("clicksByDevice", analyticsRepository.findClicksByDeviceType(shortCode));

        // Hourly breakdown (last 24 hours)
        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        analytics.put("clicksByHour", analyticsRepository.findClicksByHour(shortCode, dayAgo));

        // Top referrers
        analytics.put("topReferrers", analyticsRepository.findTopReferrers(shortCode));

        return analytics;
    }
}

