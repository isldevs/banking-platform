package com.demo.account.api.controller;

import com.demo.account.api.dto.request.Funding;
import com.demo.account.api.dto.request.OpenAccount;
import com.demo.account.api.dto.request.TransferCommand;
import com.demo.account.api.dto.response.AccountResponse;
import com.demo.account.api.dto.response.LedgerResponse;
import com.demo.account.api.dto.response.OperationResponse;
import com.demo.account.application.port.in.AccountingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AccountController {
    private final AccountingService accountingService;

    public AccountController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @PostMapping("/api/accounts")
    @PreAuthorize("hasAuthority('ACCOUNT_OPEN')")
    public AccountResponse open(@Valid @RequestBody OpenAccount request, @AuthenticationPrincipal Jwt jwt) {
        return accountingService.open(request, jwt);
    }

    @GetMapping("/api/accounts/{id}")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public AccountResponse read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return accountingService.read(id, jwt);
    }

    @GetMapping("/api/accounts")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountingService.list(jwt);
    }

    @PostMapping("/api/accounts/{id}/freeze")
    @PreAuthorize("hasAuthority('ACCOUNT_FREEZE')")
    public AccountResponse freeze(@PathVariable UUID id) {
        return accountingService.freeze(id);
    }

    @PostMapping("/api/accounts/{id}/fund")
    @PreAuthorize("hasAuthority('ACCOUNT_FUND')")
    public AccountResponse fund(@PathVariable UUID id, @Valid @RequestBody Funding request) {
        return accountingService.fund(id, request);
    }

    @GetMapping("/api/accounts/ledger/{operationId}")
    @PreAuthorize("hasAuthority('ACCOUNT_FUND')")
    public LedgerResponse ledger(@PathVariable UUID operationId) {
        return accountingService.ledger(operationId);
    }

    @PostMapping("/internal/accounts/transfer")
    public AccountResponse internalTransfer(@RequestHeader("X-Service-Key") String key, @RequestBody TransferCommand command) {
        return accountingService.internalTransfer(key, command);
    }

    @GetMapping("/internal/accounts/operations/{id}")
    public OperationResponse operation(@RequestHeader("X-Service-Key") String key, @PathVariable UUID id) {
        return accountingService.operation(key, id);
    }
}
