package com.vaibhavgala.url_shortner.service.enrichment;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

@Service
public class GeoIPService {
    private static final Logger log = LoggerFactory.getLogger(GeoIPService.class);
    private final DatabaseReader geoReader;

    public GeoIPService() throws IOException {
        DatabaseReader reader = null;
        String envPath = System.getenv("GEOIP_DB_PATH");

        if (envPath != null && !envPath.isBlank()) {
            File database = new File(envPath);
            if (database.exists()) {
                log.info("GeoLite2-City.mmdb loaded from GEOIP_DB_PATH");
                reader = new DatabaseReader.Builder(database).build();
            }
        }

        if (reader == null) {
            ClassPathResource resource = new ClassPathResource("GeoLite2-City.mmdb");
            if (resource.exists()) {
                log.info("GeoLite2-City.mmdb loaded from classpath");
                reader = new DatabaseReader.Builder(resource.getInputStream()).build();
            }
        }

        if (reader == null) {
            log.error("GeoLite2-City.mmdb NOT FOUND (set GEOIP_DB_PATH or include resource)");
        }

        this.geoReader = reader;
    }

    public String[] getCountryAndCity(String ip) {
        if (geoReader == null || isPrivateIP(ip)) {
            return new String[]{null, null};
        }
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            CityResponse response = geoReader.city(inetAddress);
            String country = response.getCountry().getName();
            String city = response.getCity().getName();
            return new String[]{
                    (country != null && !country.isEmpty()) ? country : null,
                    (city != null && !city.isEmpty()) ? city : null
            };
        } catch (Exception e) {
            log.warn("GeoIP lookup failed for {}: {}", ip, e.getMessage());
            return new String[]{null, null};
        }
    }

    private boolean isPrivateIP(String ip) {
        return ip.startsWith("127.") ||
                ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                ip.startsWith("172.16.") ||
                ip.equals("localhost") ||
                ip.equals("0:0:0:0:0:0:0:1"); // IPv6 localhost
    }
}