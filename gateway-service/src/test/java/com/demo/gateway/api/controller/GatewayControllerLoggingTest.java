package com.demo.gateway.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

class GatewayControllerLoggingTest {
  @Test
  void downstreamFailureDoesNotLogQueryCredentialsOrExceptionContent() {
    String secret = "sensitive-refresh-token";
    RestClient client = mock(RestClient.class);
    RestClient.Builder builder = mock(RestClient.Builder.class);
    when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
    when(builder.build()).thenReturn(client);
    when(client.method(any(HttpMethod.class))).thenThrow(new IllegalStateException(secret));
    GatewayController controller =
        new GatewayController(
            "http://auth",
            "http://customer",
            "http://account",
            "http://transfer",
            "http://notification",
            "http://audit",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            10,
            10,
            Duration.ofSeconds(1),
            builder);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.setQueryString("refresh_token=" + secret);
    Logger logger = (Logger) LoggerFactory.getLogger(GatewayController.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(controller.proxy(request, null).getStatusCode().value()).isEqualTo(502);
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getFormattedMessage())
          .contains("auth")
          .doesNotContain(secret);
      assertThat(appender.list.get(0).getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
