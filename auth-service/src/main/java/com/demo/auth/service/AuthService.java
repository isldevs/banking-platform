package com.demo.auth.service;

import com.demo.auth.entity.Login;
import com.demo.auth.entity.NewUser;
import com.demo.auth.entity.Refresh;
import com.demo.auth.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface AuthService {
    Map<String, Object> tokens(User u);

    Map<String, Object> refresh(Refresh req);

    Map<String, Object> login(Login req, HttpServletRequest http);

    Map<String, String> logout(Refresh req, Jwt principal);

    User lookup(String username);

    void attempt(User u, String name, boolean success, HttpServletRequest req);

    Map<String, String> create(NewUser req);
}
