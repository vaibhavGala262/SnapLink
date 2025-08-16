package com.vaibhavgala.url_shortner.service;

import com.vaibhavgala.url_shortner.models.UrlMapping;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class UrlShortnerService {
    @Autowired
    private UrlMappingRepository repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String CACHE_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public String shortenUrl(String originalUrl) {
        Optional<UrlMapping> existingMapping = repository.findByOriginalUrl(originalUrl);
        if (existingMapping.isPresent()) {
            return existingMapping.get().getShortCode();
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);
        repository.save(mapping);

        String shortCode = encodeBase62(mapping.getId());
        mapping.setShortCode(shortCode);
        repository.save(mapping);

        //  Cache the URL immediately
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl, CACHE_TTL);
            System.out.println("✅ Cached new URL: " + shortCode);
        } catch (Exception e) {
            System.out.println("⚠️ Cache failed, but continuing...");
        }

        return shortCode;
    }

    public Optional<String> getOriginalUrl(String shortCode) {
        String cacheKey = CACHE_PREFIX + shortCode;

        try {
            // Check Redis cache first
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null) {
                System.out.println("🚀 CACHE HIT for: " + shortCode);
                return Optional.of(cachedUrl);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Cache read failed, falling back to DB");
        }

        //  Cache miss - query database
        System.out.println("💾 CACHE MISS for: " + shortCode + " - querying DB");
        Optional<UrlMapping> dbResult = repository.findByShortCode(shortCode);

        // 🔥 NEW: Load result into cache for next time
        if (dbResult.isPresent()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, dbResult.get().getOriginalUrl(), CACHE_TTL);
                System.out.println("✅ Loaded into cache: " + shortCode);
            } catch (Exception e) {
                System.out.println("⚠️ Cache write failed, but continuing...");
            }
        }

        return dbResult.map(UrlMapping::getOriginalUrl);
    }

    private String encodeBase62(Long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }
}
