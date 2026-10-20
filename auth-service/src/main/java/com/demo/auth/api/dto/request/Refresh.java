package com.demo.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record Refresh(@NotBlank String refreshToken) {
}
