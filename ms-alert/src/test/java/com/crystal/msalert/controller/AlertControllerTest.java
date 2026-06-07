package com.crystal.msalert.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returnsOkWithUpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ms-alert"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void config_returnsOkWithConfigInfo() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-alert"))
                .andExpect(jsonPath("$.slack").isMap())
                .andExpect(jsonPath("$.slack.webhookConfigured").isBoolean())
                .andExpect(jsonPath("$.slack.enabled").isBoolean())
                .andExpect(jsonPath("$.kafka").isMap())
                .andExpect(jsonPath("$.kafka.topic").isNotEmpty())
                .andExpect(jsonPath("$.kafka.consumerGroup").isNotEmpty())
                .andExpect(jsonPath("$.alert.monitoredSeverities").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
