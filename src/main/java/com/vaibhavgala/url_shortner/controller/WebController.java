package com.vaibhavgala.url_shortner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/", "/index.html"})
    public String home() {
        return "index";
    }

    @GetMapping({"/analytics", "/analytics.html"})
    public String analytics() {
        return "analytics";
    }
}