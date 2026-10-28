package com.demo.gateway.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Correlation-Id";
  private static final String MDC_KEY = "correlation_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String value = req.getHeader(HEADER_NAME);
    if (value == null || !value.matches("[a-zA-Z0-9._-]{1,128}"))
      value = UUID.randomUUID().toString();
    res.setHeader(HEADER_NAME, value);
    try {
      MDC.put(MDC_KEY, value);
      chain.doFilter(req, res);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
