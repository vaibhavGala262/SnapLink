package com.vaibhavgala.url_shortner.service;

import com.vaibhavgala.url_shortner.models.UrlMapping;
import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import com.vaibhavgala.url_shortner.service.cache.CacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortnerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private UrlShortnerService service;

    @Test
    @DisplayName("Custom alias creates a mapping, persists it, caches it and returns the alias")
    void shortenWithCustomAlias_createsMappingAndReturnsAlias() {
        when(repository.findByShortCode("mycode")).thenReturn(Optional.empty());

        String result = service.shortenUrl("https://example.com", "MyCode", null);

        assertEquals("mycode", result);
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).save(captor.capture());
        assertEquals("https://example.com", captor.getValue().getOriginalUrl());
        assertEquals("mycode", captor.getValue().getShortCode());
        verify(cacheService).set(eq("url:mycode"), eq("https://example.com"), any(Duration.class));
    }

    @Test
    @DisplayName("Alias already used for a different URL throws IllegalArgumentException and saves nothing")
    void shortenWithAliasTakenByDifferentUrl_throws() {
        UrlMapping existing = mapping("taken", "https://other.com");
        when(repository.findByShortCode("taken")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.shortenUrl("https://new.com", "taken", null));

        assertTrue(ex.getMessage().contains("already used"));
        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Alias reused for the same URL returns the existing code without saving a new row")
    void shortenWithAliasReusedForSameUrl_returnsExistingWithoutSaving() {
        when(repository.findByShortCode("same")).thenReturn(Optional.of(mapping("same", "https://same.com")));

        assertEquals("same", service.shortenUrl("https://same.com", "SAME", null));

        verify(repository, never()).save(any(UrlMapping.class));
        verify(cacheService).set(eq("url:same"), eq("https://same.com"), any(Duration.class));
    }

    @Test
    @DisplayName("Invalid or reserved aliases are rejected before any persistence")
    void shortenWithInvalidAlias_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.shortenUrl("https://e.com", "ab", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.shortenUrl("https://e.com", "has space", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.shortenUrl("https://e.com", "api", null));

        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Without an alias, an existing non-expired mapping is reused")
    void shortenWithoutAlias_reusesExistingUnexpiredMapping() {
        UrlMapping existing = mapping("existing1", "https://e.com");
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(repository.findByOriginalUrl("https://e.com")).thenReturn(Optional.of(existing));

        assertEquals("existing1", service.shortenUrl("https://e.com", null, null));

        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Without an alias, a new 10-char code is generated, retrying on collisions")
    void shortenWithoutAlias_generatesNewCodeOnCollisionRetry() {
        when(repository.findByOriginalUrl("https://e.com")).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(true, false);

        String result = service.shortenUrl("https://e.com", null, null);

        assertNotNull(result);
        assertEquals(10, result.length());
        verify(repository, times(2)).existsByShortCode(anyString());
        verify(repository).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Gives up with RuntimeException when collisions keep occurring")
    void shorten_givesUpAfterCollisionRetries_throws() {
        when(repository.findByOriginalUrl("https://e.com")).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.shortenUrl("https://e.com", null, null));

        verify(repository, times(5)).existsByShortCode(anyString());
    }

    @Test
    @DisplayName("getOriginalUrl returns cached values without hitting the database")
    void getOriginalUrl_returnsFromCache_withoutTouchingDatabase() {
        when(cacheService.get("url:abc")).thenReturn("https://cached.com");

        assertEquals(Optional.of("https://cached.com"), service.getOriginalUrl("abc"));

        verify(repository, never()).findByShortCode(anyString());
    }

    @Test
    @DisplayName("getOriginalUrl populates the cache from the database on a miss")
    void getOriginalUrl_cachesDatabaseHit() {
        when(cacheService.get("url:abc")).thenReturn(null);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(mapping("abc", "https://db.com")));

        assertEquals(Optional.of("https://db.com"), service.getOriginalUrl("abc"));

        verify(cacheService).set(eq("url:abc"), eq("https://db.com"), any(Duration.class));
    }

    @Test
    @DisplayName("getOriginalUrl returns empty for expired or unknown codes")
    void getOriginalUrl_expiredOrUnknown_returnsEmpty() {
        UrlMapping expired = mapping("gone", "https://e.com");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(cacheService.get("url:gone")).thenReturn(null);
        when(repository.findByShortCode("gone")).thenReturn(Optional.of(expired));
        when(repository.findByShortCode("nope")).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.getOriginalUrl("gone"));
        assertEquals(Optional.empty(), service.getOriginalUrl("nope"));

        verify(cacheService, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private UrlMapping mapping(String code, String url) {
        UrlMapping m = new UrlMapping();
        m.setShortCode(code);
        m.setOriginalUrl(url);
        return m;
    }
}