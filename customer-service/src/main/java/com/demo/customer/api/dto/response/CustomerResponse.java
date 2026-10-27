package com.demo.customer.api.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CustomerResponse(
        UUID id,
        String customerNumber,
        UUID userId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String status) {
}
