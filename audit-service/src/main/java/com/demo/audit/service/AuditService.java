package com.demo.audit.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditService {
    List<Map<String, Object>> find(UUID id);
}
