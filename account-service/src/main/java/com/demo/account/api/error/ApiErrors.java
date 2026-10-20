package com.demo.account.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException e, HttpServletRequest req) {
        return build(e.getStatusCode().value(), e.getReason() == null ? "REQUEST_FAILED" : e.getReason(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(Exception e, HttpServletRequest req) {
        return build(400, "VALIDATION_ERROR", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> conflict(Exception e, HttpServletRequest req) {
        return build(409, "DATA_CONFLICT", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest req) {
        LoggerFactory.getLogger(ApiErrors.class).error("Unhandled request error for {}", req.getRequestURI(), e);
        return build(500, "INTERNAL_ERROR", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> json(Exception e, HttpServletRequest req) {
        return build(400, "INVALID_JSON", req);
    }

    private ResponseEntity<Map<String, Object>> build(int status, String code, HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status);
        m.put("code", code);
        m.put("message", code.replace('_', ' '));
        m.put("path", req.getRequestURI());
        m.put("correlationId", Optional.ofNullable(req.getHeader("X-Correlation-Id")).orElse(""));
        return ResponseEntity.status(status).body(m);
    }
}
