package com.demo.account.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(
    UUID id, String currency, String status, String type, UUID owner, BigDecimal balance) {}
