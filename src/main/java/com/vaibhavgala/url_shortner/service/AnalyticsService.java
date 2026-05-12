package com.vaibhavgala.url_shortner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AnalyticsService {

    private final ObjectMapper objectMapper;
    private final Parser uaParser;
    private final GeoIPService geoIpService;

    public AnalyticsService(GeoIPService geoIpService) {
        this.objectMapper = new ObjectMapper();
        this.uaParser = new Parser();
        this.geoIpService = geoIpService;
    }

    // Build entity from JSON, no DB save here
    public UrlClickAnalytics buildAnalyticsEntity(String clickEventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(clickEventJson, Map.class);

            String shortCode = (String) event.get("shortCode");
            String ipAddress = (String) event.get("ipAddress");
            String userAgent = (String) event.get("userAgent");
            String referer = (String) event.get("referer");
            String timestampStr = (String) event.get("timestamp");

//            Client client = uaParser.parse(userAgent);

//            String country = geoIpService.getCountry(ipAddress);
//            String city = geoIpService.getCity(ipAddress);

            UrlClickAnalytics analytics = new UrlClickAnalytics();
            analytics.setShortCode(shortCode);
            analytics.setTimestamp(LocalDateTime.parse(timestampStr));
            analytics.setIpAddress(ipAddress);
            analytics.setUserAgent(userAgent);
            analytics.setReferer(referer);

//            analytics.setDeviceType(getDeviceType(client));
//            analytics.setBrowser(client.userAgent.family);
//            analytics.setBrowserVersion(client.userAgent.major);
//            analytics.setOs(client.os.family);
//            analytics.setOsVersion(client.os.major);

//            analytics.setCountry(country != null && !country.equals("Unknown") ? country : null);
//            analytics.setCity(city != null && !city.equals("Unknown") ? city : null);
            analytics.setCreatedAt(LocalDateTime.now());

            return analytics;

        } catch (Exception e) {
            System.err.println("❌ Analytics building error: " + e.getMessage());
            return null;
        }
    }

    private String getDeviceType(Client client) {
        if (client.device == null || client.device.family == null) {
            return "Desktop";
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
