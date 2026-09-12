package com.vaibhavgala.url_shortner.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / and /index.html both render the index page")
    void home_rendersIndexView() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
        mockMvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(view().name("index"));
    }

    @Test
    @DisplayName("GET /analytics and /analytics.html both render the analytics page")
    void analytics_rendersAnalyticsView() throws Exception {
        mockMvc.perform(get("/analytics")).andExpect(status().isOk()).andExpect(view().name("analytics"));
        mockMvc.perform(get("/analytics.html")).andExpect(status().isOk()).andExpect(view().name("analytics"));
    }
}