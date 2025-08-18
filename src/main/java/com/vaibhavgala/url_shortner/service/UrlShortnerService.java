package com.vaibhavgala.url_shortner.service;

import com.vaibhavgala.url_shortner.config.GeoIPConfig;
import com.vaibhavgala.url_shortner.models.UrlMapping;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class UrlShortnerService {

    @Autowired
    private UrlMappingRepository repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // SECURE RANDOM CODE GENERATION CONSTANTS
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RETRIES = 5;

    // Cache configuration
    private static final String CACHE_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    // Reserved aliases that can't be used
    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api", "admin", "www", "analytics", "dashboard", "login", "signup", "help",
            "about", "contact", "terms", "privacy", "support", "docs", "blog"
    );

    /**
     * Generating cryptographically secure random short code
     * 10-character Base62 string = 62^10 = 839 quintillion combinations
     */
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = RANDOM.nextInt(ALPHABET.length());
            code.append(ALPHABET.charAt(randomIndex));
        }
        return code.toString();
    }

    /**
     * Creates unique short code with collision detection
     * Extremely low collision probability with 62^10 keyspace
     */
    private String createUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = generateRandomCode();

            if (!repository.existsByShortCode(code)) {
                System.out.println("✅ Generated unique code: " + code + " (attempt " + (attempt + 1) + ")");
                return code;
            }

            System.out.println("⚠️ Collision detected: " + code + " (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
        }

        throw new RuntimeException("Failed to generate unique short code after " + MAX_RETRIES + " attempts. Consider increasing code length.");
    }

    /**
     * Validates custom alias format
     */
    private boolean isValidAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return false;
        }

        alias = alias.trim().toLowerCase();

        // Check format: 3-20 chars, alphanumeric + hyphens/underscores
        if (!alias.matches("^[a-zA-Z0-9-_]{3,20}$")) {
            return false;
        }

        // Check if reserved
        if (RESERVED_ALIASES.contains(alias)) {
            return false;
        }

        return true;
    }

    /**
     * Creates new mapping and saves to DB and cache
     */
    private String createNewMapping(String originalUrl, String shortCode, LocalDateTime expiresAt) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);
        mapping.setShortCode(shortCode);
        mapping.setExpiresAt(expiresAt);
        mapping.setCreatedAt(LocalDateTime.now());

        // Save to database
        repository.save(mapping);

        // Cache the new mapping
        try {
            Duration cacheTTL = expiresAt != null ?
                    Duration.between(LocalDateTime.now(), expiresAt) :
                    Duration.ofHours(24);

            redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl, cacheTTL);
            System.out.println("💾 Cached new URL: " + shortCode + " → " + originalUrl);
        } catch (Exception e) {
            System.out.println("⚠️ Cache failed, continuing without cache: " + e.getMessage());
        }

        System.out.println("🎉 Created: " + shortCode + " → " + originalUrl);
        return shortCode;
    }

    /**
     * Re-caches existing mapping and returns shortCode
     */
    private String reuseExistingMapping(UrlMapping existing, String originalUrl) {
        try {
            Duration remainingTTL = existing.getExpiresAt() != null ?
                    Duration.between(LocalDateTime.now(), existing.getExpiresAt()) :
                    Duration.ofHours(24);

            redisTemplate.opsForValue().set(CACHE_PREFIX + existing.getShortCode(), originalUrl, remainingTTL);
            System.out.println("💾 Re-cached existing URL: " + existing.getShortCode());
        } catch (Exception e) {
            System.out.println("⚠️ Re-caching failed: " + e.getMessage());
        }

        return existing.getShortCode();
    }

    /**
     * Creates shortened URL with optional custom alias and expiration
     * Flow: Check cache → Check DB → Create new (if needed)
   */
    public String shortenUrl(String originalUrl, String customAlias, LocalDateTime expiresAt) {
        boolean hasCustomAlias = (customAlias != null && !customAlias.trim().isEmpty());

        if (hasCustomAlias) {

            String alias = customAlias.trim();

            // Validate alias format
            if (!isValidAlias(alias)) {
                throw new IllegalArgumentException("Invalid custom alias. Use 3-20 characters (letters, numbers, hyphens, underscores only)");
            }

            // Check if alias already exists
            Optional<UrlMapping> existingAlias = repository.findByShortCode(alias);
            if (existingAlias.isPresent()) {
                UrlMapping existing = existingAlias.get();

                // Same URL + Same Alias = Return existing (industry standard)
                if (existing.getOriginalUrl().equals(originalUrl)) {
                    System.out.println("📎 Returning existing alias for same URL: " + alias);
                    return reuseExistingMapping(existing, originalUrl);
                } else {
                    // Different URL + Same Alias = Error
                    throw new IllegalArgumentException("Alias '" + alias + "' is already used for a different URL");
                }
            }

            // Create new mapping with custom alias
            System.out.println("🎯 Using custom alias: " + alias);
            return createNewMapping(originalUrl, alias, expiresAt);

        } else {


            // Check if URL already exists in DB
            Optional<UrlMapping> existingMapping = repository.findByOriginalUrl(originalUrl);
            if (existingMapping.isPresent()) {
                UrlMapping existing = existingMapping.get();

                // Check if existing URL is not expired
                if (!existing.isExpired()) {
                    System.out.println("📎 Reusing existing generated code for URL: " + originalUrl);
                    return reuseExistingMapping(existing, originalUrl);
                }
            }

            // Generate new unique code
            String shortCode = createUniqueShortCode();
            System.out.println("🎲 Generated new code: " + shortCode);
            return createNewMapping(originalUrl, shortCode, expiresAt);
        }
    }

    /**
     * Retrieves original URL with caching and expiration checks
     */
    public Optional<String> getOriginalUrl(String shortCode) {
        // Check Redis cache first (fastest lookup)
        String cacheKey = CACHE_PREFIX + shortCode;
        try {
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null) {
                System.out.println("🚀 Cache HIT for: " + shortCode);
                return Optional.of(cachedUrl);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Cache lookup failed, falling back to DB: " + e.getMessage());
        }

        // Query database if cache miss
        Optional<UrlMapping> dbResult = repository.findByShortCode(shortCode);

        if (dbResult.isPresent()) {
            UrlMapping mapping = dbResult.get();

            // Check if URL has expired
            if (mapping.isExpired()) {
                System.out.println("⏰ URL expired: " + shortCode);
                return Optional.empty();
            }

            // Cache for future requests if not expired
            try {
                Duration remainingTTL = mapping.getExpiresAt() != null ?
                        Duration.between(LocalDateTime.now(), mapping.getExpiresAt()) :
                        CACHE_TTL;

                redisTemplate.opsForValue().set(cacheKey, mapping.getOriginalUrl(), remainingTTL);
                System.out.println("💾 DB→Cache: " + shortCode + " → " + mapping.getOriginalUrl());
            } catch (Exception e) {
                System.out.println("⚠️ Caching failed: " + e.getMessage());
            }

            return Optional.of(mapping.getOriginalUrl());
        }

        System.out.println("❌ Short code not found: " + shortCode);
        return Optional.empty();
    }

    /**
     * Utility method to check if a short code exists
     */
    public boolean shortCodeExists(String shortCode) {
        return repository.existsByShortCode(shortCode);
    }

    /**
     * Get statistics about code generation
     */
    public String getCodeGenerationStats() {
        long totalCodes = repository.count();
        double keyspaceUtilization = (totalCodes / 3521614606208.0) * 100;

        return String.format(
                "Total codes: %d, Keyspace utilization: %.8f%%",
                totalCodes, keyspaceUtilization);
    }
}
