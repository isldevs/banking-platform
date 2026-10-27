package com.demo.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
public class AccountBalanceEntity {
    @Id
    @Column(name = "account_id")
    private UUID accountId;
    @Column(name = "ledger_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal ledgerBalance = BigDecimal.ZERO;
    @Column(name = "available_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(BigDecimal ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
