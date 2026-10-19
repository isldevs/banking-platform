package com.demo.auth.entity;

import jakarta.validation.constraints.NotBlank;

public record Login(@NotBlank String username, @NotBlank String password) {
}
