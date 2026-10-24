package com.demo.gateway.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
public class GatewayController {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration CONNECTION_REQUEST_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_CONNECTIONS = 200;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 50;

    private final RestClient client;
    private final Map<String, String> targets;

    GatewayController(@Value("${services.auth-url}") String auth,
                      @Value("${services.customer-url}") String customer,
                      @Value("${services.account-url}") String account,
                      @Value("${services.transfer-url}") String transfer,
                      @Value("${services.notification-url}") String notification,
                      @Value("${services.audit-url}") String audit) {
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
                        .setMaxConnTotal(MAX_CONNECTIONS)
                        .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                        .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()
                .evictIdleConnections(Duration.ofSeconds(30))
                .disableAutomaticRetries()
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        client = RestClient.builder().requestFactory(factory).build();
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
                if (res.getHeaders().getContentType() != null) {
                    headers.setContentType(res.getHeaders().getContentType());
                }
                if (res.getHeaders().containsKey("X-Correlation-Id")) {
                    headers.set("X-Correlation-Id", res.getHeaders().getFirst("X-Correlation-Id"));
                }
                return new ResponseEntity<>(res.getBody().readAllBytes(), headers, res.getStatusCode());
            });
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body("{\"code\":\"DOWNSTREAM_UNAVAILABLE\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void copyForwardedHeaders(HttpServletRequest request, HttpHeaders headers) {
        for (String name : List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "Idempotency-Key",
                "X-Correlation-Id")) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.set(name, value);
            }
        }
    }
}
