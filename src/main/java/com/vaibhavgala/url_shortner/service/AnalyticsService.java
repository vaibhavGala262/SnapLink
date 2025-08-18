package com.vaibhavgala.url_shortner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.repo.UrlClickAnalyticsRepository;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AnalyticsService {

    private final UrlClickAnalyticsRepository analyticsRepository;
    private final ObjectMapper objectMapper;
    private final Parser uaParser;
    private final GeoIPService geoIpService; // ✅ Fixed variable name

    public AnalyticsService(UrlClickAnalyticsRepository analyticsRepository, GeoIPService geoIpService) {
        this.analyticsRepository = analyticsRepository;
        this.objectMapper = new ObjectMapper();
        this.uaParser = new Parser();
        this.geoIpService = geoIpService; // ✅ Fixed assignment
    }

    public void processClickEvent(String clickEventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(clickEventJson, Map.class);

            String shortCode = (String) event.get("shortCode");
            String ipAddress = (String) event.get("ipAddress");
            String userAgent = (String) event.get("userAgent");
            String referer = (String) event.get("referer");
            String timestampStr = (String) event.get("timestamp");

            // Parse User Agent
            Client client = uaParser.parse(userAgent);

            //  Get GeoIP info using individual methods
            String country = geoIpService.getCountry(ipAddress);
            String city = geoIpService.getCity(ipAddress);

            // Create analytics record
            UrlClickAnalytics analytics = new UrlClickAnalytics();
            analytics.setShortCode(shortCode);
            analytics.setTimestamp(LocalDateTime.parse(timestampStr));
            analytics.setIpAddress(ipAddress);
            analytics.setUserAgent(userAgent);
            analytics.setReferer(referer);

            // Device and browser info
            analytics.setDeviceType(getDeviceType(client));
            analytics.setBrowser(client.userAgent.family);
            analytics.setBrowserVersion(client.userAgent.major);
            analytics.setOs(client.os.family);
            analytics.setOsVersion(client.os.major);

            // ✅ Set geo data with safe fallback
            analytics.setCountry(country != null && !country.equals("Unknown") ? country : null);
            analytics.setCity(city != null && !city.equals("Unknown") ? city : null);

            // Save to database
            analyticsRepository.save(analytics);

            System.out.println("✅ Analytics saved: " + shortCode + " from " +
                    (country != null ? country : "Unknown") + " on " +
                    analytics.getDeviceType());

        } catch (Exception e) {
            System.err.println("❌ Analytics processing error: " + e.getMessage());
        }
    }

    private String getDeviceType(Client client) {
        if (client.device == null || client.device.family == null) {
            return "Desktop"; // Default fallback
        }

        String device = client.device.family.toLowerCase();
        if (device.contains("mobile") || device.contains("phone")) {
            return "Mobile";
        } else if (device.contains("tablet") || device.contains("ipad")) {
            return "Tablet";
        } else {
            return "Desktop";
        }
    }
}
