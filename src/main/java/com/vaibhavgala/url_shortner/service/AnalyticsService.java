package com.vaibhavgala.url_shortner.service;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import com.vaibhavgala.url_shortner.service.events.ClickEvent;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.time.LocalDateTime;

@Service
public class AnalyticsService {

    private final Parser uaParser;
    private final GeoIPService geoIpService;

    public AnalyticsService(GeoIPService geoIpService) {
        this.uaParser = new Parser();
        this.geoIpService = geoIpService;
    }

    public UrlClickAnalytics buildAnalyticsEntity(ClickEvent event) {
        if (event == null || event.shortCode() == null) {
            return null;
        }

        UrlClickAnalytics analytics = new UrlClickAnalytics();
        analytics.setShortCode(event.shortCode());
        analytics.setTimestamp(event.timestamp());
        analytics.setIpAddress(event.ipAddress());
        analytics.setUserAgent(event.userAgent());
        analytics.setReferer(event.referer());

        Client client = null;
        if (event.userAgent() != null && !event.userAgent().isBlank()) {
            client = uaParser.parse(event.userAgent());
        }
        if (client != null) {
            analytics.setDeviceType(getDeviceType(client));
            analytics.setBrowser(client.userAgent != null ? client.userAgent.family : null);
            analytics.setBrowserVersion(client.userAgent != null ? client.userAgent.major : null);
            analytics.setOs(client.os != null ? client.os.family : null);
            analytics.setOsVersion(client.os != null ? client.os.major : null);
        }

        String[] geo = geoIpService.getCountryAndCity(event.ipAddress());
        analytics.setCountry(geo[0]);
        analytics.setCity(geo[1]);

        analytics.setCreatedAt(LocalDateTime.now());
        return analytics;
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