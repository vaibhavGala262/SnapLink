package com.vaibhavgala.url_shortner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebController {

    @GetMapping("/")
    public String home() {
        return "index"; // This will serve the index.html template
    }

    @GetMapping("/analytics")
    public String analytics() {
        return "analytics"; // This will serve the index.html template
    }

    @GetMapping("/index.html")
    public String myhome() {
        return "index"; // This will serve the index.html template
    }

    @GetMapping("/analytics.html")
    public String myanalytics() {
        return "analytics"; // This will serve the index.html template
    }




}