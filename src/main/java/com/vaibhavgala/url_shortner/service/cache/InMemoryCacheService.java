package com.vaibhavgala.url_shortner.service.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "app.features.redis.enabled", havingValue = "false")
public class InMemoryCacheService implements CacheService {

    private static class CacheEntry {
        String value;
        LocalDateTime expiresAt;

        CacheEntry(String value, LocalDateTime expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public void set(String key, String value, Duration ttl) {
        LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);
        cache.put(key, new CacheEntry(value, expiresAt));
        System.out.println("💾 [InMemoryCache] Cached: " + key + " (ttl=" + ttl.toSeconds() + "s)");
    }

    @Override
    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                cache.remove(key);
                System.out.println("⏰ [InMemoryCache] Expired: " + key);
                return null;
            }
            return entry.value;
        }
        return null;
    }
}
