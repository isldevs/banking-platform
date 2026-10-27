package com.demo.gateway.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
public class GatewayController {

    private final static Logger log = LoggerFactory.getLogger(GatewayController.class);
    private final RestClient client;
    private final Map<String, String> targets;

    GatewayController(@Value("${services.auth-url}") String auth,
                      @Value("${services.customer-url}") String customer,
                      @Value("${services.account-url}") String account,
                      @Value("${services.transfer-url}") String transfer,
                      @Value("${services.notification-url}") String notification,
                      @Value("${services.audit-url}") String audit,
                      @Value("${gateway.http-client.connect-timeout}") Duration connectTimeout,
                      @Value("${gateway.http-client.connection-request-timeout}") Duration connectionRequestTimeout,
                      @Value("${gateway.http-client.read-timeout}") Duration readTimeout,
                      @Value("${gateway.http-client.max-connections}") int maxConnections,
                      @Value("${gateway.http-client.max-connections-per-route}") int maxConnectionsPerRoute,
                      @Value("${gateway.http-client.idle-connection-eviction}") Duration idleConnectionEviction,
                      RestClient.Builder restClientBuilder) {
        targets = Map.of(
                "auth", auth,
                "users", auth,
                "customers", customer,
                "accounts", account,
                "transfers", transfer,
                "notifications", notification,
                "audit", audit
        );

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnectionsPerRoute)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMilliseconds(idleConnectionEviction.toMillis()))
                .disableAutomaticRetries()
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        client = restClientBuilder.requestFactory(factory).build();
    }

    @RequestMapping(value = "/api/**", method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE
    })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        if (parts.length < 3 || !targets.containsKey(parts[2])) {
            return ResponseEntity.notFound().build();
        }

        String dest = targets.get(parts[2]) + path
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());

        try {
            RestClient.RequestBodySpec call = client.method(HttpMethod.valueOf(request.getMethod()))
                    .uri(URI.create(dest))
                    .headers(headers -> copyForwardedHeaders(request, headers));

            if (body != null && body.length > 0) {
                call.body(body);
            }

            return call.exchange((req, res) -> {
                HttpHeaders headers = new HttpHeaders();
                copyResponseHeaders(res.getHeaders(), headers);
                headers.set("X-Content-Type-Options", "nosniff");

                byte[] responseBody = res.getBody() == null
                        ? new byte[0]
                        : res.getBody().readAllBytes();

                return new ResponseEntity<>(responseBody, headers, res.getStatusCode());
            });
        } catch (Exception e) {
            log.error("Gateway downstream request failed: {}", dest, e);
            return ResponseEntity.status(502)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header("X-Content-Type-Options", "nosniff")
                    .body("{\"code\":\"DOWNSTREAM_UNAVAILABLE\"}"
                                  .getBytes(StandardCharsets.UTF_8));
        }
    }

    private void copyForwardedHeaders(HttpServletRequest request, HttpHeaders headers) {
        for (String name : List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "Idempotency-Key",
                "X-Service-Key",
                "X-External-Reference",
                "X-Correlation-Id")) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.set(name, value);
            }
        }
    }

    private void copyResponseHeaders(HttpHeaders source, HttpHeaders target) {
        for (String name : List.of(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.CONTENT_DISPOSITION,
                HttpHeaders.CACHE_CONTROL,
                HttpHeaders.ETAG,
                HttpHeaders.LAST_MODIFIED,
                HttpHeaders.LOCATION,
                HttpHeaders.RETRY_AFTER,
                HttpHeaders.ALLOW,
                "X-Correlation-Id")) {
            List<String> values = source.get(name);
            if (values != null) {
                values.forEach(value -> target.add(name, value));
            }
        }
    }
}
