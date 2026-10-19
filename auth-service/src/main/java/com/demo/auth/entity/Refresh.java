package com.demo.auth.entity;

import jakarta.validation.constraints.NotBlank;

public record Refresh(@NotBlank String refreshToken) {
}
