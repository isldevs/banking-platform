package com.demo.config.entity;

import jakarta.validation.constraints.NotBlank;

public record Configuration(@NotBlank String service, @NotBlank String environment, @NotBlank String key, @NotBlank String value) {
}
