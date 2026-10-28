package com.demo.transfer.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.transfer.application.service.TransferService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestHeader;

class TransferControllerContractTest {
  private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID TRANSFER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

  @Test
  void missingRolesClaimIsHandledAsNonAdmin() {
    TransferService service = mock(TransferService.class);
    Map<String, Object> expected = Map.of("id", TRANSFER_ID.toString());
    when(service.view(TRANSFER_ID, USER_ID, false)).thenReturn(expected);

    TransferController controller = new TransferController(service);
    assertEquals(expected, controller.view(TRANSFER_ID, jwt(false)));

    verify(service).view(TRANSFER_ID, USER_ID, false);
  }

  @Test
  void adminClaimIsForwardedToOwnerCheck() {
    TransferService service = mock(TransferService.class);
    TransferController controller = new TransferController(service);

    controller.view(TRANSFER_ID, jwt(true));

    verify(service).view(TRANSFER_ID, USER_ID, true);
  }

  @Test
  void idempotencyKeyHeaderIsRequiredAtHttpBoundary() throws Exception {
    RequestHeader header =
        TransferController.class
            .getMethod("create", String.class, TransferService.NewTransfer.class, Jwt.class)
            .getParameters()[0]
            .getAnnotation(RequestHeader.class);

    assertTrue(header.required());
    assertEquals("Idempotency-Key", header.value());
  }

  private static Jwt jwt(boolean admin) {
    Jwt.Builder jwt =
        Jwt.withTokenValue("test-token").header("alg", "RS256").subject(USER_ID.toString());
    if (admin) jwt.claim("roles", List.of("ADMIN"));
    return jwt.build();
  }
}
