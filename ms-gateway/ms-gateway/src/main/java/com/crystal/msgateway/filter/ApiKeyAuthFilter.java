package com.crystal.msgateway.filter;

import com.crystal.msgateway.model.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GlobalFilter that:
 *  1. Skips authentication for /actuator/** and /health/** paths.
 *  2. Validates the X-API-Key header against the configured secret.
 *  3. Enforces in-memory per-IP rate limiting (20 req/s, burst via Guava RateLimiter).
 *
 * Runs at order -1 so it executes before all other filters.
 */
@Slf4j
@Component
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final double PERMITS_PER_SECOND = 20.0;

    private final String apiKey;
    private final ObjectMapper objectMapper;

    // Per-IP Guava RateLimiters — created lazily, one per client IP
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public ApiKeyAuthFilter(
            @Value("${app.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip auth & rate limiting for management/health endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Resolve client IP
        String clientIp = resolveClientIp(request);

        // Rate limiting check — try to acquire a permit without waiting
        RateLimiter limiter = rateLimiters.computeIfAbsent(
                clientIp,
                ip -> RateLimiter.create(PERMITS_PER_SECOND)
        );

        if (!limiter.tryAcquire()) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return writeErrorResponse(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Maximum " + (int) PERMITS_PER_SECOND + " requests/second allowed.",
                    path
            );
        }

        // API key validation
        String providedKey = request.getHeaders().getFirst(API_KEY_HEADER);

        if (providedKey == null || providedKey.isBlank()) {
            log.debug("Missing {} header for path: {}", API_KEY_HEADER, path);
            return writeErrorResponse(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Missing required header: " + API_KEY_HEADER,
                    path
            );
        }

        if (!apiKey.equals(providedKey)) {
            log.warn("Invalid API key presented from IP: {} for path: {}", clientIp, path);
            return writeErrorResponse(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid API key",
                    path
            );
        }

        return chain.filter(exchange);
    }

    /**
     * Returns true for paths that are publicly accessible without authentication.
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator") || path.startsWith("/health");
    }

    /**
     * Resolves the client IP from X-Forwarded-For or the direct remote address.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return Objects.requireNonNullElse(
                request.getRemoteAddress() != null
                        ? request.getRemoteAddress().getAddress().getHostAddress()
                        : null,
                "unknown"
        );
    }

    /**
     * Writes a JSON error response to the ServerHttpResponse.
     */
    private Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            HttpStatus status,
            String message,
            String path) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
