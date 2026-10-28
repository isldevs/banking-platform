package com.demo.customer.application.port.in;

import com.demo.customer.api.dto.request.NewCustomer;
import com.demo.customer.api.dto.response.CustomerResponse;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface CustomerService {
  CustomerResponse create(NewCustomer request, Jwt jwt);

  CustomerResponse read(UUID id, Jwt jwt);

  CustomerResponse approve(UUID id, Jwt jwt);

  CustomerResponse internal(UUID id, String key);
}
