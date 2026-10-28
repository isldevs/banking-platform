package com.demo.account.api.dto.response;

import java.util.UUID;

public record OperationResponse(UUID transferId, String status) {}
