package com.demo.gateway.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService service;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimitService service, RateLimitProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.enabled() || !request.getRequestURI().startsWith("/api/")) {
            return true;
        }

        try {
            RateLimitService.Decision decision = service.check(request);
            response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()));

            if (decision.allowed()) {
                return true;
            }

            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setHeader("Retry-After", String.valueOf(decision.resetSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests\"}");
            return false;
        } catch (Exception e) {
            if (properties.failOpen()) {
                log.warn("Rate-limit store unavailable; allowing request", e);
                return true;
            }
            log.error("Rate-limit store unavailable; rejecting request", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMIT_UNAVAILABLE\",\"message\":\"Request protection is temporarily unavailable\"}");
            return false;
        }
    }
}
