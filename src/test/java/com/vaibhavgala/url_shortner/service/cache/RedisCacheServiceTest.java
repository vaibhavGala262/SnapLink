package com.vaibhavgala.url_shortner.service.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisCacheService redisCacheService;

    @Test
    @DisplayName("Delegates set/get to the Redis template operations")
    void setAndGet_delegateToRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisCacheService.set("url:abc", "https://example.com", Duration.ofMinutes(5));
        verify(valueOperations).set(eq("url:abc"), eq("https://example.com"), any(Duration.class));

        when(valueOperations.get("url:abc")).thenReturn("https://example.com");
        assertEquals("https://example.com", redisCacheService.get("url:abc"));
    }

    @Test
    @DisplayName("A Redis outage is swallowed: get returns null and set does not throw")
    void redisUnavailable_noThrow() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Connection refused"));

        assertNull(redisCacheService.get("url:abc"));
        assertDoesNotThrow(() -> redisCacheService.set("url:abc", "value", Duration.ofMinutes(1)));
    }
}