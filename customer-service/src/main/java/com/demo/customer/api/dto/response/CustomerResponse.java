package com.demo.customer.api.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String customerNumber,
    UUID userId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String status) {}
