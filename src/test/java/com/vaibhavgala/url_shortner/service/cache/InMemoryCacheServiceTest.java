package com.vaibhavgala.url_shortner.service.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryCacheServiceTest {

    private final InMemoryCacheService cache = new InMemoryCacheService();

    @Test
    @DisplayName("Values round-trip through the in-memory cache")
    void setAndGet_roundTripsValue() {
        cache.set("url:key1", "https://example.com", Duration.ofMinutes(5));

        assertEquals("https://example.com", cache.get("url:key1"));
    }

    @Test
    @DisplayName("Expired entries are dropped and unknown keys return null")
    void get_expiredEntryOrUnknownKey_returnsNull() {
        cache.set("url:expired", "https://example.com", Duration.ofMillis(-10));

        assertNull(cache.get("url:expired"));
        assertNull(cache.get("url:neverSet"));
    }
}