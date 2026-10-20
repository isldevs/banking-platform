package com.demo.auth.application.port.out;

import java.util.List;
import java.util.UUID;

public interface TokenService {
    String access(UUID id, String username, List<String> permissions, List<String> roles);
}
