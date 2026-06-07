package com.crystal.msgateway.controller;

import com.crystal.msgateway.model.ServiceHealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregates the health status of all downstream services and exposes it
 * at GET /health/services. Uses WebClient with a 2-second timeout per service;
 * any error/timeout results in UNKNOWN status.
 */
@Slf4j
@RestController
@RequestMapping("/health")
public class HealthAggregatorController {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String STATUS_UP      = "UP";
    private static final String STATUS_DOWN    = "DOWN";
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    private final WebClient.Builder webClientBuilder;
    private final String msIntakeUrl;
    private final String msResultsUrl;
    private final String msAlertUrl;

    public HealthAggregatorController(
            WebClient.Builder webClientBuilder,
            @Value("${downstream.ms-intake.url:http://ms-intake:8081}") String msIntakeUrl,
            @Value("${downstream.ms-results.url:http://ms-results:8082}") String msResultsUrl,
            @Value("${downstream.ms-alert.url:http://ms-alert:8083}") String msAlertUrl) {
        this.webClientBuilder = webClientBuilder;
        this.msIntakeUrl  = msIntakeUrl;
        this.msResultsUrl = msResultsUrl;
        this.msAlertUrl   = msAlertUrl;
    }

    @GetMapping("/services")
    public Mono<ResponseEntity<ServiceHealthResponse>> servicesHealth() {
        Mono<String> intakeStatus  = checkService("ms-intake",  msIntakeUrl);
        Mono<String> resultsStatus = checkService("ms-results", msResultsUrl);
        Mono<String> alertStatus   = checkService("ms-alert",   msAlertUrl);

        return Mono.zip(intakeStatus, resultsStatus, alertStatus)
                .map(tuple -> {
                    Map<String, String> services = new HashMap<>();
                    services.put("ms-intake",  tuple.getT1());
                    services.put("ms-results", tuple.getT2());
                    services.put("ms-alert",   tuple.getT3());

                    boolean allUp = services.values().stream().allMatch(STATUS_UP::equals);
                    boolean anyDown = services.values().stream().anyMatch(STATUS_DOWN::equals);

                    String overall = allUp ? STATUS_UP : (anyDown ? STATUS_DOWN : STATUS_UNKNOWN);

                    ServiceHealthResponse body = ServiceHealthResponse.builder()
                            .overall(overall)
                            .services(services)
                            .build();

                    return ResponseEntity.ok(body);
                });
    }

    /**
     * Calls /actuator/health on the given base URL and returns UP, DOWN, or UNKNOWN.
     */
    private Mono<String> checkService(String name, String baseUrl) {
        return webClientBuilder.build()
                .get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .map(body -> {
                    Object status = body.get("status");
                    return STATUS_UP.equals(status) ? STATUS_UP : STATUS_DOWN;
                })
                .onErrorResume(ex -> {
                    log.warn("Health check failed for {}: {}", name, ex.getMessage());
                    return Mono.just(STATUS_UNKNOWN);
                });
    }
}
