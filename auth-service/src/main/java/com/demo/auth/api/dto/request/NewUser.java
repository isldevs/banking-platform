package com.demo.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record NewUser(@NotBlank String username, @NotBlank String password, String email) {
}
