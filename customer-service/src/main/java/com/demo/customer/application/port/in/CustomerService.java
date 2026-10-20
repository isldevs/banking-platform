package com.demo.customer.application.port.in;

import com.demo.customer.api.dto.request.NewCustomer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

public interface CustomerService {
    Map<String, Object> create(NewCustomer request, Jwt jwt);

    Map<String, Object> read(UUID id, Jwt jwt);

    Map<String, Object> approve(UUID id, Jwt jwt);

    Map<String, Object> internal(UUID id, String key);
}
