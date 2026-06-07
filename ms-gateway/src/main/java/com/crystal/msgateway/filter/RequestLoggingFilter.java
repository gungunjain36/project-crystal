package com.crystal.msgateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalFilter that logs each incoming request and the resulting response status.
 * Runs at order 0, after the ApiKeyAuthFilter (order -1).
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path   = request.getPath().value();
        String query  = request.getURI().getRawQuery();
        String fullPath = query != null ? path + "?" + query : path;

        log.debug("Incoming request: {} {}", method, fullPath);

        long start = System.currentTimeMillis();

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("{} {} → {} ({}ms)", method, fullPath, statusCode, elapsed);
                })
                .doOnError(err ->
                        log.error("{} {} → ERROR: {}", method, fullPath, err.getMessage())
                );
    }
}
