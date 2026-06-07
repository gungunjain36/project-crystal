package com.crystal.msgateway.controller;

import com.crystal.msgateway.model.ServiceHealthResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HealthAggregatorController using MockWebServer
 * to simulate downstream service health endpoints.
 */
class HealthAggregatorControllerTest {

    private MockWebServer mockIntake;
    private MockWebServer mockResults;
    private MockWebServer mockAlert;
    private HealthAggregatorController controller;

    private static final String UP_BODY   = "{\"status\":\"UP\"}";
    private static final String DOWN_BODY = "{\"status\":\"DOWN\"}";

    @BeforeEach
    void setUp() throws IOException {
        mockIntake  = new MockWebServer();
        mockResults = new MockWebServer();
        mockAlert   = new MockWebServer();

        mockIntake.start();
        mockResults.start();
        mockAlert.start();

        controller = new HealthAggregatorController(
                WebClient.builder(),
                mockIntake.url("").toString().replaceAll("/$", ""),
                mockResults.url("").toString().replaceAll("/$", ""),
                mockAlert.url("").toString().replaceAll("/$", "")
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockIntake.shutdown();
        mockResults.shutdown();
        mockAlert.shutdown();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("All services UP → overall=UP, all statuses UP")
    void allServicesUp_returnsOverallUp() {
        mockIntake.enqueue(jsonResponse(UP_BODY));
        mockResults.enqueue(jsonResponse(UP_BODY));
        mockAlert.enqueue(jsonResponse(UP_BODY));

        Mono<ResponseEntity<ServiceHealthResponse>> result = controller.servicesHealth();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(200);
                    ServiceHealthResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getOverall()).isEqualTo("UP");
                    assertThat(body.getServices()).containsEntry("ms-intake",  "UP");
                    assertThat(body.getServices()).containsEntry("ms-results", "UP");
                    assertThat(body.getServices()).containsEntry("ms-alert",   "UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("One service DOWN → overall=DOWN, mixed statuses")
    void oneServiceDown_returnsOverallDown() {
        mockIntake.enqueue(jsonResponse(UP_BODY));
        mockResults.enqueue(jsonResponse(DOWN_BODY));
        mockAlert.enqueue(jsonResponse(UP_BODY));

        Mono<ResponseEntity<ServiceHealthResponse>> result = controller.servicesHealth();

        StepVerifier.create(result)
                .assertNext(response -> {
                    ServiceHealthResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getOverall()).isEqualTo("DOWN");
                    assertThat(body.getServices()).containsEntry("ms-intake",  "UP");
                    assertThat(body.getServices()).containsEntry("ms-results", "DOWN");
                    assertThat(body.getServices()).containsEntry("ms-alert",   "UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Service unreachable → UNKNOWN status")
    void unreachableService_returnsUnknown() throws IOException {
        // Shut down the intake server to simulate a connection failure
        mockIntake.shutdown();
        mockResults.enqueue(jsonResponse(UP_BODY));
        mockAlert.enqueue(jsonResponse(UP_BODY));

        Mono<ResponseEntity<ServiceHealthResponse>> result = controller.servicesHealth();

        StepVerifier.create(result)
                .assertNext(response -> {
                    ServiceHealthResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getServices()).containsEntry("ms-intake", "UNKNOWN");
                    assertThat(body.getServices()).containsEntry("ms-results", "UP");
                    assertThat(body.getServices()).containsEntry("ms-alert",   "UP");
                })
                .verifyComplete();
    }
}
