package com.crystal.msgateway.filter;

import com.crystal.msgateway.model.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private static final String VALID_KEY = "test-secret-key";

    private ApiKeyAuthFilter filter;
    private GatewayFilterChain chain;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new ApiKeyAuthFilter(VALID_KEY, objectMapper);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Missing X-API-Key header returns 401")
    void missingApiKey_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/scans/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Wrong X-API-Key header returns 401")
    void wrongApiKey_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/scans/123")
                .header("X-API-Key", "wrong-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Correct X-API-Key header passes filter and calls chain")
    void correctApiKey_callsChain() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/scans/123")
                .header("X-API-Key", VALID_KEY)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // status set by chain, not filter
    }

    @Test
    @DisplayName("/actuator/health is accessible without API key")
    void actuatorPath_skipAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("/health/services is accessible without API key")
    void healthPath_skipAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/health/services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Filter is ordered at -1")
    void filterOrder_isMinusOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Rapid requests from same IP trigger rate limit 429")
    void rateLimitExceeded_returns429() {
        // Send many requests rapidly — at 20 req/s one of them should fail
        // We use 100 back-to-back requests to reliably exceed the burst capacity
        MockServerWebExchange lastRejected = null;

        for (int i = 0; i < 100; i++) {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/scans/test")
                    .remoteAddress(new java.net.InetSocketAddress("192.168.1.99", 1234))
                    .header("X-API-Key", VALID_KEY)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            filter.filter(exchange, chain).block();

            if (HttpStatus.TOO_MANY_REQUESTS.equals(exchange.getResponse().getStatusCode())) {
                lastRejected = exchange;
                break;
            }
        }

        assertThat(lastRejected).as("Expected at least one 429 after 100 rapid requests").isNotNull();
        assertThat(lastRejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
