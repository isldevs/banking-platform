package com.demo.gateway.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RateLimitPropertiesTest {

  @Test
  void matchesFirstConfiguredRule() {
    RateLimitProperties properties =
        new RateLimitProperties(
            true,
            false,
            60,
            Duration.ofMinutes(1),
            List.of(
                new RateLimitProperties.Rule(
                    "transfer", HttpMethod.POST, "/api/transfers", 10, Duration.ofMinutes(1))));

    RateLimitProperties.Rule rule = properties.match(HttpMethod.POST, "/api/transfers/123");

    assertThat(rule.name()).isEqualTo("transfer");
    assertThat(rule.limit()).isEqualTo(10);
  }

  @Test
  void fallsBackToDefaultRule() {
    RateLimitProperties properties =
        new RateLimitProperties(true, false, 60, Duration.ofMinutes(1), List.of());

    RateLimitProperties.Rule rule = properties.match(HttpMethod.GET, "/api/accounts/123");

    assertThat(rule.name()).isEqualTo("default");
    assertThat(rule.limit()).isEqualTo(60);
  }
}
