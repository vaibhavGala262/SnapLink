package com.vaibhavgala.url_shortner.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ClientIPService {

    // Add this method for testing different IPs
    public String getClientIP(HttpServletRequest request) {
        // For testing: Check headers first (you can comment these out after testing)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }

        String xClientIP = request.getHeader("X-Client-IP");
        if (xClientIP != null && !xClientIP.isEmpty() && !"unknown".equalsIgnoreCase(xClientIP)) {
            return xClientIP;
        }

        // Fallback to actual IP (your current method)
        return request.getRemoteAddr();
    }

}
