package com.vaibhavgala.url_shortner.service.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.features.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisCacheService implements CacheService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            System.out.println("⚠️ Redis Cache set failed: " + e.getMessage());
        }
    }

    @Override
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            System.out.println("⚠️ Redis Cache get failed: " + e.getMessage());
            return null;
        }
    }
}
