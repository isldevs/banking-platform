package com.demo.account.application.port.in;

import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

public interface AccountingService {
  OperationResponse operation(UUID id);

  AccountResponse open(OpenAccount request, Jwt jwt, String idempotencyKey);

  AccountResponse read(UUID id, Jwt jwt);

  List<AccountResponse> list(Jwt jwt);

  AccountResponse freeze(UUID id);

  @Transactional
  AccountResponse fund(UUID id, Funding request);

  LedgerResponse ledger(UUID operationId);

  @Transactional
  AccountResponse internalTransfer(String key, TransferCommand command);

  @Transactional
  AccountResponse postFundingInstruction(String key, UUID instructionId, String externalReference);

  OperationResponse operation(String key, UUID id);
}
