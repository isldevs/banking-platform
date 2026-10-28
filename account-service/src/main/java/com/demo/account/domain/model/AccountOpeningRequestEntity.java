package com.demo.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_opening_requests")
public class AccountOpeningRequestEntity {
  @Id
  @Column(name = "idempotency_key", length = 120)
  private String idempotencyKey;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "account_id", nullable = false, unique = true)
  private UUID accountId;

  @Column(name = "product_code", nullable = false, length = 40)
  private String productCode;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "requested_initial_deposit", nullable = false, precision = 20, scale = 2)
  private BigDecimal requestedInitialDeposit;

  @Column(name = "funding_channel", length = 30)
  private String fundingChannel;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public void setCustomerId(UUID customerId) {
    this.customerId = customerId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getRequestedInitialDeposit() {
    return requestedInitialDeposit;
  }

  public void setRequestedInitialDeposit(BigDecimal requestedInitialDeposit) {
    this.requestedInitialDeposit = requestedInitialDeposit;
  }

  public String getFundingChannel() {
    return fundingChannel;
  }

  public void setFundingChannel(String fundingChannel) {
    this.fundingChannel = fundingChannel;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
