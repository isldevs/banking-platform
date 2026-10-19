package com.demo.customer.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record NewCustomer(@NotBlank String firstName, @NotBlank String lastName, @NotNull @Past LocalDate dateOfBirth, String phone, String email) {
}
