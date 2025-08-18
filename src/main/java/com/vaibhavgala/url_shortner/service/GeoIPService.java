package com.vaibhavgala.url_shortner.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

@Service
public class GeoIPService {
    private final DatabaseReader geoReader;

    public GeoIPService() throws IOException {
        File database = new File("src/main/resources/GeoLite2-City.mmdb");
        if (!database.exists()) {
            System.err.println("❌ GeoLite2-City.mmdb NOT FOUND at: " + database.getAbsolutePath());
            this.geoReader = null;
        } else {
            System.out.println("✅ GeoLite2-City.mmdb loaded successfully");
            this.geoReader = new DatabaseReader.Builder(database).build();
        }
    }

    public String getCountry(String ip) {
        System.out.println("🔍 Looking up country for IP: " + ip);

        if (geoReader == null) {
            System.err.println("❌ GeoReader is null - database not loaded");
            return "Unknown";
        }

        if (isPrivateIP(ip)) {
            System.out.println("⚠️ Private IP detected: " + ip + " - returning Unknown");
            return "Unknown";
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            CityResponse response = geoReader.city(inetAddress);
            String country = response.getCountry().getName();

            System.out.println("🌍 Country lookup result for " + ip + ": " + country);
            return (country != null && !country.isEmpty()) ? country : "Unknown";
        } catch (Exception e) {
            System.err.println("❌ GeoIP Country lookup failed for " + ip + ": " + e.getMessage());
            e.printStackTrace();
            return "Unknown";
        }
    }

    public String getCity(String ip) {
        System.out.println("🔍 Looking up city for IP: " + ip);

        if (geoReader == null) {
            System.err.println("❌ GeoReader is null - database not loaded");
            return "Unknown";
        }

        if (isPrivateIP(ip)) {
            System.out.println("⚠️ Private IP detected: " + ip + " - returning Unknown");
            return "Unknown";
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            CityResponse response = geoReader.city(inetAddress);
            String city = response.getCity().getName();

            System.out.println("🏙️ City lookup result for " + ip + ": " + city);
            return (city != null && !city.isEmpty()) ? city : "Unknown";
        } catch (Exception e) {
            System.err.println("❌ GeoIP City lookup failed for " + ip + ": " + e.getMessage());
            e.printStackTrace();
            return "Unknown";
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
