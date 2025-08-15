package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.service.UrlShortnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class UrlShortenerController {

    private final UrlShortnerService service;

    public UrlShortenerController(UrlShortnerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<String> shorten(@RequestParam String url) {
        String shortCode = service.shortenUrl(url);
        String shortUrl = "https://snaplink-ov05.onrender.com/" + shortCode;
        return ResponseEntity.ok(shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Object> redirect(@PathVariable String shortCode) {
        return service.getOriginalUrl(shortCode)
                .map(originalUrl -> ResponseEntity
                        .status(302)
                        .location(URI.create(originalUrl))
                        .build())
                .orElse(ResponseEntity.notFound().build());
    }
}
