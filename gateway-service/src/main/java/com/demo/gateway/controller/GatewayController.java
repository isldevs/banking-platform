package com.demo.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    private final RestClient client;
    private final Map<String, String> targets;

    GatewayController(@Value("${services.auth-url}") String auth,
                      @Value("${services.customer-url}") String customer,
                      @Value("${services.account-url}") String account,
                      @Value("${services.transfer-url}") String transfer,
                      @Value("${services.notification-url}") String notification,
                      @Value("${services.audit-url}") String audit,
                      @Value("${services.config-url}") String config) {
        targets = Map.of("auth", auth, "users", auth, "customers", customer, "accounts", account, "transfers", transfer, "notifications", notification, "audit", audit, "config", config);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().requestFactory(factory).build();
    }

    @RequestMapping(value = "/api/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        if (parts.length < 3 || !targets.containsKey(parts[2]))
            return ResponseEntity.notFound().build();
        String dest = targets.get(parts[2]) + path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        try {
            RestClient.RequestBodySpec call = client.method(HttpMethod.valueOf(request.getMethod())).uri(URI.create(dest))
                    .headers(h -> {
                        for (String name : List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id")) {
                            String v = request.getHeader(name);
                            if (v != null)
                                h.set(name, v);
                        }
                    });
            if (body != null && body.length > 0)
                call.body(body);
            return call.exchange((req, res) -> {
                HttpHeaders headers = new HttpHeaders();
                if (res.getHeaders().getContentType() != null)
                    headers.setContentType(res.getHeaders().getContentType());
                if (res.getHeaders().containsKey("X-Correlation-Id"))
                    headers.set("X-Correlation-Id", res.getHeaders().getFirst("X-Correlation-Id"));
                return new ResponseEntity<>(res.getBody().readAllBytes(), headers, res.getStatusCode());
            });
        } catch (Exception e) {
            return ResponseEntity.status(502).body("{\"code\":\"DOWNSTREAM_UNAVAILABLE\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
