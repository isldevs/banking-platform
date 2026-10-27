package com.demo.gateway.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('INCR', KEYS[1]) "
                            + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                            + "local ttl = redis.call('PTTL', KEYS[1]) "
                            + "return {current, ttl}",
                    List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Decision check(HttpServletRequest request) {
        if (!properties.enabled() || !request.getRequestURI().startsWith("/api/")) {
            return Decision.allowed(0, 0, 0);
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        RateLimitProperties.Rule rule = properties.match(method, request.getRequestURI());
        String key = "gateway:rate-limit:v1:" + identity(request) + ":" + rule.name();

        List result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(rule.window().toMillis()));

        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Rate-limit store returned an invalid response");
        }

        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = Math.max(0, ((Number) result.get(1)).longValue());
        long remaining = Math.max(0, rule.limit() - count);
        long resetSeconds = Math.max(1, (ttlMillis + 999) / 1000);

        return new Decision(count <= rule.limit(), rule.limit(), remaining, resetSeconds);
    }

    private String identity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !authentication.getName().isBlank()
                && !"anonymousUser".equals(authentication.getName())) {
            return "user:" + sanitize(authentication.getName());
        }
        return "ip:" + sanitize(request.getRemoteAddr());
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._:-]", "_");
    }

    public record Decision(boolean allowed, long limit, long remaining, long resetSeconds) {
        static Decision allowed(long limit, long remaining, long resetSeconds) {
            return new Decision(true, limit, remaining, resetSeconds);
        }
    }
}
