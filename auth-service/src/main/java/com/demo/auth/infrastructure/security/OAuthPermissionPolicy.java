package com.demo.auth.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The user's permissions and the client's delegated scopes must both allow an operation. */
final class OAuthPermissionPolicy {
  static final String ADMIN_SCOPE = "banking.admin";

  static final Set<String> CUSTOMER_SCOPES =
      Set.of(
          "customer.read",
          "customer.write",
          "account.read",
          "account.write",
          "transfer.create",
          "transfer.read",
          "notification.read");

  private static final Map<String, Set<String>> SCOPE_PERMISSIONS =
      Map.of(
          "customer.read",
          Set.of("CUSTOMER_READ"),
          "customer.write",
          Set.of("CUSTOMER_CREATE"),
          "account.read",
          Set.of("ACCOUNT_READ"),
          "account.write",
          Set.of("ACCOUNT_OPEN"),
          "transfer.create",
          Set.of("TRANSFER_CREATE"),
          "transfer.read",
          Set.of("TRANSFER_READ"),
          "notification.read",
          Set.of("NOTIFICATION_READ"),
          "audit.read",
          Set.of("AUDIT_READ"),
          ADMIN_SCOPE,
          Set.of(
              "USER_READ",
              "USER_MANAGE",
              "KYC_APPROVE",
              "ACCOUNT_FREEZE",
              "ACCOUNT_FUND",
              "AUDIT_READ"));

  private OAuthPermissionPolicy() {}

  static List<String> permissions(Collection<String> userPermissions, Set<String> grantedScopes) {
    Set<String> delegated =
        grantedScopes.stream()
            .flatMap(scope -> SCOPE_PERMISSIONS.getOrDefault(scope, Set.of()).stream())
            .collect(Collectors.toSet());
    return userPermissions.stream()
        .filter(delegated::contains)
        .distinct()
        .sorted()
        .collect(Collectors.toCollection(ArrayList::new));
  }

  static List<String> roles(Collection<String> userRoles, Set<String> grantedScopes) {
    // ADMIN is also used by ownership checks. Merely dropping administrative authorities would
    // still allow a read-only public-client token to inspect other customers' accounts.
    return userRoles.stream()
        .filter(role -> !"ADMIN".equals(role) || grantedScopes.contains(ADMIN_SCOPE))
        .distinct()
        .sorted()
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
