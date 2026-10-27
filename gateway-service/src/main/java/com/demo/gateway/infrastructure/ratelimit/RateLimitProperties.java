package com.demo.gateway.infrastructure.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        boolean failOpen,
        long defaultLimit,
        Duration defaultWindow,
        List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (defaultLimit < 1) {
            throw new IllegalArgumentException("gateway.rate-limit.default-limit must be positive");
        }
        if (defaultWindow.isZero() || defaultWindow.isNegative()) {
            throw new IllegalArgumentException("gateway.rate-limit.default-window must be positive");
        }
    }

    public Rule match(HttpMethod method, String path) {
        return rules.stream()
                .filter(rule -> rule.method() == method)
                .filter(rule -> path.startsWith(rule.pathPrefix()))
                .findFirst()
                .orElse(new Rule("default", method, "/api/", defaultLimit, defaultWindow));
    }

    public record Rule(
            String name,
            HttpMethod method,
            String pathPrefix,
            long limit,
            Duration window) {
        public Rule {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Rate-limit rule name is required");
            }
            if (method == null) {
                throw new IllegalArgumentException("Rate-limit rule method is required");
            }
            if (pathPrefix == null || pathPrefix.isBlank()) {
                throw new IllegalArgumentException("Rate-limit rule path-prefix is required");
            }
            if (limit < 1) {
                throw new IllegalArgumentException("Rate-limit rule limit must be positive");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Rate-limit rule window must be positive");
            }
        }
    }
}
