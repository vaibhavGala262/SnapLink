package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.service.ClientIPService;
import com.vaibhavgala.url_shortner.service.UrlShortnerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;

import com.vaibhavgala.url_shortner.service.events.EventProducer;

@RestController
public class UrlShortenerController {

    private final UrlShortnerService service;

    @Autowired
    private Environment env;

    @Autowired
    private ClientIPService clientIPService;

    @Autowired
    private EventProducer eventProducer;

    public UrlShortenerController(UrlShortnerService service) {
        this.service = service;
    }

    // SINGLE /shorten endpoint with OPTIONAL expiry
    @PostMapping("/api/shorten")
    public ResponseEntity<String> shorten(
            @RequestParam String url,
            @RequestParam(required = false) String alias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiresAt,
            HttpServletRequest request) {

        String prefix = env.getProperty("PREFIX_WEBSITE_DOMAIN");
        if (prefix == null || prefix.isBlank()) {
            String scheme = request.getScheme();
            String host = request.getHeader("Host");
            prefix = scheme + "://" + host + "/";
        }
        String shortCode = service.shortenUrl(url, alias, expiresAt); // Pass expiry (can be null)
        String shortUrl = prefix + shortCode;
        return ResponseEntity.ok(shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Object> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Optional<String> originalUrl = service.getOriginalUrl(shortCode);
        // String IP_ADDRESS = clientIPService.getClientIP(request);

        if (originalUrl.isPresent()) {
            // Send click event (non-blocking for Kafka, blocking for Sync)
            eventProducer.sendClickEvent(
                    shortCode,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    request.getHeader("Referer"));

            return ResponseEntity.status(302).location(URI.create(originalUrl.get())).build();
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
