package com.demo.auth.entity;

import jakarta.validation.constraints.NotBlank;

public record NewUser(@NotBlank String username, @NotBlank String password, String email) {
}
