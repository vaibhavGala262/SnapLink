package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.service.UrlShortnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.env.Environment;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class UrlShortenerController {

    private final UrlShortnerService service;

    @Autowired
    private Environment env;

    public UrlShortenerController(UrlShortnerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<String> shorten(@RequestParam String url) {
        String prefix= env.getProperty("PREFIX_WEBSITE_DOMAIN");
        String shortCode = service.shortenUrl(url);
        String shortUrl = prefix + shortCode;
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
