package com.demo.account.application.port.in;

import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

public interface AccountingService {
    OperationResponse operation(UUID id);

    AccountResponse open(OpenAccount request, Jwt jwt);

    AccountResponse read(UUID id, Jwt jwt);

    List<AccountResponse> list(Jwt jwt);

    AccountResponse freeze(UUID id);

    AccountResponse fund(UUID id, Funding request);

    LedgerResponse ledger(UUID operationId);

    AccountResponse internalTransfer(String key, TransferCommand command);

    OperationResponse operation(String key, UUID id);
}
