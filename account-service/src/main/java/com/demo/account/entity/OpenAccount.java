package com.demo.account.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenAccount(@NotNull UUID customerId, @NotBlank String currency) {
}
