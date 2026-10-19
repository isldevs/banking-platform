package com.demo.config;

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
    private final JdbcTemplate db;

    ConfigController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/{service}/{environment}")
    @PreAuthorize("hasAuthority('CONFIG_READ')")
    public Map<String, Object> read(@PathVariable String service, @PathVariable String environment) {
        Map<String, String> properties = new LinkedHashMap<>();
        db.query(
                "SELECT DISTINCT ON(config_key) config_key,config_value FROM configurations WHERE service_name=? AND environment=? ORDER BY config_key,version DESC", r -> {
                    properties.put(r.getString(1), r.getString(2));
                }, service, environment);
        return Map.of("service", service, "environment", environment, "properties", properties);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONFIG_MANAGE')")
    public Map<String, Object> add(@Valid @RequestBody Configuration c) {
        Integer next = db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM configurations WHERE service_name=? AND environment=? AND config_key=?", Integer.class, c.service(), c.environment(), c.key());
        db.update("INSERT INTO configurations(id,service_name,environment,config_key,config_value,version) VALUES(?,?,?,?,?,?)", UUID.randomUUID(), c.service(), c.environment(), c.key(), c.value(), next);
        return Map.of("version", next, "service", c.service(), "environment", c.environment(), "key", c.key());
    }

    public record Configuration(@NotBlank String service, @NotBlank String environment, @NotBlank String key, @NotBlank String value) {
    }
}
