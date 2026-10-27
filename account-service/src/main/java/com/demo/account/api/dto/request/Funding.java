package com.demo.account.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record Funding(@NotNull BigDecimal amount) {
}
