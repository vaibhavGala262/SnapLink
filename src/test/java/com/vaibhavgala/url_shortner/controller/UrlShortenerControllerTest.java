package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.service.UrlShortnerService;
import com.vaibhavgala.url_shortner.service.enrichment.ClientIPService;
import com.vaibhavgala.url_shortner.service.events.EventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlShortenerController.class)
@TestPropertySource(properties = "PREFIX_WEBSITE_DOMAIN=https://snap.link/")
class UrlShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortnerService service;

    @MockitoBean
    private ClientIPService clientIPService;

    @MockitoBean
    private EventProducer eventProducer;

    @Test
    @DisplayName("POST /api/shorten returns the configured-domain-prefixed short URL")
    void shorten_returnsPrefixedShortUrl() throws Exception {
        when(service.shortenUrl("https://example.com", null, null)).thenReturn("abc123");

        mockMvc.perform(post("/api/shorten").param("url", "https://example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("https://snap.link/abc123"));

        verify(service).shortenUrl("https://example.com", null, null);
    }

    @Test
    @DisplayName("POST /api/shorten forwards alias and expiry timestamp to the service")
    void shorten_forwardsAliasAndExpiry() throws Exception {
        when(service.shortenUrl(eq("https://example.com"), eq("myalias"), any(LocalDateTime.class)))
                .thenReturn("myalias");

        mockMvc.perform(post("/api/shorten")
                        .param("url", "https://example.com")
                        .param("alias", "myalias")
                        .param("expiresAt", "2026-05-01T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("https://snap.link/myalias"));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(service).shortenUrl(eq("https://example.com"), eq("myalias"), captor.capture());
        assertEquals(LocalDateTime.of(2026, 5, 1, 12, 0), captor.getValue());
    }

    @Test
    @DisplayName("POST /api/shorten maps IllegalArgumentException to a 400 with the message")
    void shorten_aliasConflict_returns400() throws Exception {
        when(service.shortenUrl("https://e.com", "api", null))
                .thenThrow(new IllegalArgumentException("Invalid custom alias"));

        mockMvc.perform(post("/api/shorten").param("url", "https://e.com").param("alias", "api"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid custom alias")));
    }

    @Test
    @DisplayName("GET /{code} redirects with 302 Location and emits a click event")
    void redirect_found_returns302AndEmitsEvent() throws Exception {
        when(service.getOriginalUrl("abc")).thenReturn(Optional.of("https://target.com"));
        when(clientIPService.getClientIP(any())).thenReturn("9.9.9.9");

        mockMvc.perform(get("/abc")
                        .header("User-Agent", "Mozilla/5.0 TestAgent")
                        .header("Referer", "https://source.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://target.com"));

        verify(eventProducer).sendClickEvent("abc", "9.9.9.9", "Mozilla/5.0 TestAgent", "https://source.com");
    }

    @Test
    @DisplayName("GET /{code} for an unknown code returns 404 and emits no click event")
    void redirect_notFound_returns404() throws Exception {
        when(service.getOriginalUrl("nope")).thenReturn(Optional.empty());
        when(clientIPService.getClientIP(any())).thenReturn("0.0.0.0");

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());

        verify(eventProducer, never()).sendClickEvent(any(), any(), any(), any());
    }
}