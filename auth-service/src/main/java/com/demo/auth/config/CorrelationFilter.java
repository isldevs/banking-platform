package com.demo.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String value = req.getHeader("X-Correlation-Id");
        if (value == null || !value.matches("[a-zA-Z0-9._-]{1,128}"))
            value = UUID.randomUUID().toString();
        res.setHeader("X-Correlation-Id", value);
        try {
            MDC.put("correlationId", value);
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
