package com.demo.account.controller;

import com.demo.account.entity.Funding;
import com.demo.account.entity.OpenAccount;
import com.demo.account.entity.TransferCommand;
import com.demo.account.service.AccountingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AccountController {

    private final AccountingService accountingService;

    @Autowired
    AccountController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @PostMapping("/api/accounts")
    @PreAuthorize("hasAuthority('ACCOUNT_OPEN')")
    @Transactional
    public Map<String, Object> open(@Valid @RequestBody OpenAccount c, @AuthenticationPrincipal Jwt jwt) {
        return accountingService.open(c, jwt);
    }

    @GetMapping("/api/accounts/{id}")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public Map<String, Object> read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return accountingService.read(id, jwt);
    }

    @GetMapping("/api/accounts")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
        return accountingService.list(jwt);
    }

    @PostMapping("/api/accounts/{id}/freeze")
    @PreAuthorize("hasAuthority('ACCOUNT_FREEZE')")
    public Map<String, Object> freeze(@PathVariable UUID id) {
        return accountingService.freeze(id);
    }

    @PostMapping("/api/accounts/{id}/fund")
    @PreAuthorize("hasAuthority('ACCOUNT_FUND')")
    public Map<String, Object> fund(@PathVariable UUID id, @Valid @RequestBody Funding c) {
        return accountingService.fund(id, c);
    }

    @GetMapping("/api/accounts/ledger/{operationId}")
    @PreAuthorize("hasAuthority('ACCOUNT_FUND')")
    public Map<String, Object> ledger(@PathVariable UUID operationId) {
        return accountingService.ledger(operationId);
    }

    @PostMapping("/internal/accounts/transfer")
    public Map<String, Object> internalTransfer(@RequestHeader("X-Service-Key") String key, @RequestBody TransferCommand c) {
        return accountingService.internalTransfer(key, c);
    }

    @GetMapping("/internal/accounts/operations/{id}")
    public Map<String, Object> operation(@RequestHeader("X-Service-Key") String key, @PathVariable UUID id) {
        return accountingService.operation(key, id);
    }
}
