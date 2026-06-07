package com.crystal.msgateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * KeyResolver bean used when Spring Cloud Gateway's RequestRateLimiter filter is configured.
 * Resolves the rate-limit key from the X-Forwarded-For header, falling back to the
 * remote address. Named "ipKeyResolver" so it can be referenced in application.yml
 * as #{@ipKeyResolver}.
 *
 * NOTE: The actual per-IP token-bucket limiting is implemented in ApiKeyAuthFilter
 * using Guava RateLimiter (no Redis required). This bean exists to satisfy the
 * RequestRateLimiter filter contract should it be enabled via YAML in the future.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // Take the first IP from a potential comma-separated chain
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            return Mono.just(
                    Objects.requireNonNull(
                            exchange.getRequest().getRemoteAddress(),
                            "Remote address must not be null"
                    ).getAddress().getHostAddress()
            );
        };
    }
}
