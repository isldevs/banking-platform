package com.demo.config.controller;

import com.demo.config.entity.Configuration;
import com.demo.config.service.ConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/{service}/{environment}")
    @PreAuthorize("hasAuthority('CONFIG_READ')")
    public Map<String, Object> read(@PathVariable String service, @PathVariable String environment) {
        return configService.read(service, environment);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONFIG_MANAGE')")
    public Map<String, Object> add(@Valid @RequestBody Configuration c) {
        return configService.add(c);
    }
}
