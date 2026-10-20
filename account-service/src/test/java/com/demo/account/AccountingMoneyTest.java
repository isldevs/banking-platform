package com.demo.account;

import com.demo.account.application.service.AccountingServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountingMoneyTest {
    @Test
    void usdPreservesExactDecimal() {
        assertEquals(new BigDecimal("123.40"), AccountingServiceImpl.money(new BigDecimal("123.4"), "USD"));
    }

    @Test
    void khmerRielNeedsWholeUnits() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> AccountingServiceImpl.money(new BigDecimal("1.50"), "KHR"));
    }
}
