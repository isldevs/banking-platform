package com.demo.auth.application.port.in;

import com.demo.auth.api.dto.request.Login;
import com.demo.auth.api.dto.request.NewUser;
import com.demo.auth.api.dto.request.Refresh;
import com.demo.auth.domain.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {
  Map<String, Object> tokens(User u);

  Map<String, Object> refresh(Refresh req);

  Map<String, Object> login(Login req, HttpServletRequest http);

  Map<String, String> logout(Refresh req, Jwt principal);

  User lookup(String username);

  void attempt(User u, String name, boolean success, HttpServletRequest req);

  Map<String, String> create(NewUser req);
}
