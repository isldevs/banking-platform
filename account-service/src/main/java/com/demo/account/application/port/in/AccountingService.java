package com.demo.account.application.port.in;

import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.request.TransferCommand;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AccountingService {

    Map<String, Object> operation(UUID id);

    Map<String, Object> open(OpenAccount c, Jwt jwt);

    Map<String, Object> read(UUID id, Jwt jwt);

    List<Map<String, Object>> list(Jwt jwt);

    Map<String, Object> freeze(UUID id);

    Map<String, Object> fund(UUID id, Funding c);

    Map<String, Object> ledger(UUID operationId);

    Map<String, Object> internalTransfer(String key, TransferCommand c);

    Map<String, Object> operation(String key, UUID id);
}
