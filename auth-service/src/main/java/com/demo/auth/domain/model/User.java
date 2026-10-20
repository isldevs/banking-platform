package com.demo.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String username, String status, Integer failed, Instant locked, String hash) {
}
