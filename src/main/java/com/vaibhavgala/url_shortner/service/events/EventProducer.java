package com.vaibhavgala.url_shortner.service.events;

public interface EventProducer {
    void sendClickEvent(String shortCode, String ipAddress, String userAgent, String referer);
}
