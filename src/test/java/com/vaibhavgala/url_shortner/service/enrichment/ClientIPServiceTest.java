package com.vaibhavgala.url_shortner.service.enrichment;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIPServiceTest {

    @Mock
    private HttpServletRequest request;

    private final ClientIPService service = new ClientIPService();

    @Test
    @DisplayName("X-Forwarded-For wins and only the first address is used")
    void getClientIP_usesFirstForwardedAddress() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 10.0.0.1");

        assertEquals("1.2.3.4", service.getClientIP(request));
    }

    @Test
    @DisplayName("Unknown X-Forwarded-For falls through to X-Real-IP")
    void getClientIP_skipsUnknownForwardedFor_usesXRealIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn("5.6.7.8");

        assertEquals("5.6.7.8", service.getClientIP(request));
    }

    @Test
    @DisplayName("Falls back to the raw remote address when no proxy headers exist")
    void getClientIP_fallsBackToRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", service.getClientIP(request));
    }
}