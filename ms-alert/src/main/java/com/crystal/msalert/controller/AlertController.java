package com.crystal.msalert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Alert service health and configuration endpoints")
public class AlertController {

    @Value("${app.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${app.slack.enabled:true}")
    private boolean slackEnabled;

    @Value("${app.alert.severities:high,critical}")
    private String alertSeverities;

    @Value("${spring.kafka.consumer.group-id:ms-alert-group}")
    private String consumerGroupId;

    @Value("${kafka.topics.scan-results:scan-results}")
    private String scanResultsTopic;

    @Operation(summary = "Health check", description = "Returns the current health status of the ms-alert service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "ms-alert");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Configuration info", description = "Returns the current configuration of the ms-alert service including Slack and Kafka settings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully")
    })
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        boolean webhookConfigured = webhookUrl != null && !webhookUrl.isBlank();

        List<String> severityList = Arrays.stream(alertSeverities.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        Map<String, Object> slackConfig = new LinkedHashMap<>();
        slackConfig.put("webhookConfigured", webhookConfigured);
        slackConfig.put("enabled", slackEnabled);

        Map<String, Object> kafkaConfig = new LinkedHashMap<>();
        kafkaConfig.put("topic", scanResultsTopic);
        kafkaConfig.put("consumerGroup", consumerGroupId);

        Map<String, Object> alertConfig = new LinkedHashMap<>();
        alertConfig.put("monitoredSeverities", severityList);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "ms-alert");
        response.put("slack", slackConfig);
        response.put("kafka", kafkaConfig);
        response.put("alert", alertConfig);
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }
}
