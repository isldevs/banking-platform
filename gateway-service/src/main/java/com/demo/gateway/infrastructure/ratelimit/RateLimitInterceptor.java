package com.demo.gateway.infrastructure.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
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

      response.setHeader("Retry-After", String.valueOf(decision.resetSeconds()));
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (properties.failOpen()) {
        log.warn("Rate-limit store unavailable; allowing request", e);
        return true;
      }
      log.error("Rate-limit store unavailable; rejecting request", e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", e);
    }
  }
}
