package com.demo.account.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenAccount(
        @NotNull UUID customerId,
        @NotBlank String productCode,
        @NotBlank String currency,
        @NotNull @DecimalMin("0.00") @Digits(integer = 18, fraction = 2) BigDecimal initialDeposit,
        String fundingChannel) {
}
