package com.crystal.msgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * Programmatic route definitions for ms-gateway.
 *
 * Routes:
 *   /api/v1/scans/**   → ms-intake  (MS_INTAKE_URL)
 *   /api/v1/results/** → ms-results (MS_RESULTS_URL)
 *   /api/v1/alerts/**  → ms-alert   (MS_ALERT_URL)
 *
 * Each route removes the X-Internal-Request header before forwarding.
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${downstream.ms-intake.url:http://ms-intake:8081}")
    private String msIntakeUrl;

    @Value("${downstream.ms-results.url:http://ms-results:8082}")
    private String msResultsUrl;

    @Value("${downstream.ms-alert.url:http://ms-alert:8083}")
    private String msAlertUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

                // ms-intake: handles scan submission and management
                .route("ms-intake", r -> r
                        .path("/api/v1/scans/**")
                        .filters(f -> f
                                .removeRequestHeader("X-Internal-Request")
                                .removeRequestHeader("X-Forwarded-For")
                                .addRequestHeader("X-Gateway-Source", "ms-gateway")
                        )
                        .uri(msIntakeUrl)
                )

                // ms-results: handles scan result retrieval
                .route("ms-results", r -> r
                        .path("/api/v1/results/**")
                        .filters(f -> f
                                .removeRequestHeader("X-Internal-Request")
                                .removeRequestHeader("X-Forwarded-For")
                                .addRequestHeader("X-Gateway-Source", "ms-gateway")
                        )
                        .uri(msResultsUrl)
                )

                // ms-alert: handles alerting and notifications
                .route("ms-alert", r -> r
                        .path("/api/v1/alerts/**")
                        .filters(f -> f
                                .removeRequestHeader("X-Internal-Request")
                                .removeRequestHeader("X-Forwarded-For")
                                .addRequestHeader("X-Gateway-Source", "ms-gateway")
                        )
                        .uri(msAlertUrl)
                )

                .build();
    }
}
