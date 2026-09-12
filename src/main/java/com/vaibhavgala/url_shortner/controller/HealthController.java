package com.vaibhavgala.url_shortner.controller;

import com.vaibhavgala.url_shortner.repo.UrlMappingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final UrlMappingRepository urlMappingRepository;

    public HealthController(UrlMappingRepository urlMappingRepository) {
        this.urlMappingRepository = urlMappingRepository;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of("status", "UP", "urls", urlMappingRepository.count());
    }
}