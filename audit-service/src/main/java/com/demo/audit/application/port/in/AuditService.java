package com.demo.audit.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditService {
  List<Map<String, Object>> find(UUID id);
}
