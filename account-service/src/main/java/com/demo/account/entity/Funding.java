package com.demo.account.entity;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record Funding(@NotNull BigDecimal amount) {
}
