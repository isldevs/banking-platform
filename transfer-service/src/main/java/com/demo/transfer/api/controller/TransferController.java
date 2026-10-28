package com.demo.transfer.api.controller;

import com.demo.transfer.application.service.TransferService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
  private final TransferService service;

  TransferController(TransferService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('TRANSFER_CREATE')")
  public Map<String, Object> create(
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody TransferService.NewTransfer request,
      @AuthenticationPrincipal Jwt jwt) {
    return service.create(request, UUID.fromString(jwt.getSubject()), key);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('TRANSFER_READ')")
  public Map<String, Object> view(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    boolean admin = roles != null && roles.contains("ADMIN");
    return service.view(id, UUID.fromString(jwt.getSubject()), admin);
  }
}
