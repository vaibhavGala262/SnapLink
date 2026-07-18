package com.vaibhavgala.url_shortner.service.cache;

import java.time.Duration;

public interface CacheService {
    void set(String key, String value, Duration ttl);
    String get(String key);
}
