package com.vaibhavgala.url_shortner.service.events;

import java.time.LocalDateTime;

public record ClickEvent(
        String shortCode,
        String ipAddress,
        String userAgent,
        String referer,
        LocalDateTime timestamp) {
}