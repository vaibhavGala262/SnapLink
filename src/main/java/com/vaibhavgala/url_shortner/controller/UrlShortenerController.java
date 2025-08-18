package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.service.ClientIPService;
import com.vaibhavgala.url_shortner.service.KafkaClickProducer;
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

@RestController
@RequestMapping("/api")
public class UrlShortenerController {

    private final UrlShortnerService service;

    @Autowired
    private Environment env;

    @Autowired
    private ClientIPService clientIPService;

    @Autowired
    private KafkaClickProducer kafkaClickProducer;

    public UrlShortenerController(UrlShortnerService service) {
        this.service = service;
    }

    //  SINGLE /shorten endpoint with OPTIONAL expiry
    @PostMapping("/shorten")
    public ResponseEntity<String> shorten(
            @RequestParam String url,
            @RequestParam(required = false) String alias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiresAt) {

        String prefix = env.getProperty("PREFIX_WEBSITE_DOMAIN");
        String shortCode = service.shortenUrl(url,alias, expiresAt);  // Pass expiry (can be null)
        String shortUrl = prefix + shortCode;
        return ResponseEntity.ok(shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Object> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Optional<String> originalUrl = service.getOriginalUrl(shortCode);
//        String IP_ADDRESS = clientIPService.getClientIP(request);

        if (originalUrl.isPresent()) {
            // KAFKA: Send click event (non-blocking, 1-2ms)
            kafkaClickProducer.sendClickEvent(
                    shortCode,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    request.getHeader("Referer")
            );

            return ResponseEntity.status(302).location(URI.create(originalUrl.get())).build();
        }
        return ResponseEntity.notFound().build();
    }
}
