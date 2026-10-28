package com.demo.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.demo.account.application.service.AccountingServiceImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountingMoneyTest {
  @Test
  void usdPreservesExactDecimal() {
    assertEquals(
        new BigDecimal("123.40"), AccountingServiceImpl.money(new BigDecimal("123.4"), "USD"));
  }

  @Test
  void khmerRielNeedsWholeUnits() {
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> AccountingServiceImpl.money(new BigDecimal("1.50"), "KHR"));
  }
}
