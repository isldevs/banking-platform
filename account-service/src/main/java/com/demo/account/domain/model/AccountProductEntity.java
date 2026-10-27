package com.demo.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_products")
public class AccountProductEntity {
    @Id
    @Column(name = "product_code", length = 40)
    private String productCode;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "initial_deposit_required", nullable = false)
    private boolean initialDepositRequired;

    @Column(name = "initial_deposit_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal initialDepositAmount;

    @Column(name = "minimum_opening_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal minimumOpeningBalance;

    @Column(name = "minimum_operating_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal minimumOperatingBalance;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isInitialDepositRequired() { return initialDepositRequired; }
    public void setInitialDepositRequired(boolean initialDepositRequired) { this.initialDepositRequired = initialDepositRequired; }
    public BigDecimal getInitialDepositAmount() { return initialDepositAmount; }
    public void setInitialDepositAmount(BigDecimal initialDepositAmount) { this.initialDepositAmount = initialDepositAmount; }
    public BigDecimal getMinimumOpeningBalance() { return minimumOpeningBalance; }
    public void setMinimumOpeningBalance(BigDecimal minimumOpeningBalance) { this.minimumOpeningBalance = minimumOpeningBalance; }
    public BigDecimal getMinimumOperatingBalance() { return minimumOperatingBalance; }
    public void setMinimumOperatingBalance(BigDecimal minimumOperatingBalance) { this.minimumOperatingBalance = minimumOperatingBalance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
