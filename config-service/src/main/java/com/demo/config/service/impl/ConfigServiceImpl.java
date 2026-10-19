package com.demo.config.service.impl;

import com.demo.config.entity.Configuration;
import com.demo.config.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfigServiceImpl implements ConfigService {

    private final JdbcTemplate db;

    @Autowired
    public ConfigServiceImpl(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public Map<String, Object> read(String service, String environment) {
        Map<String, String> properties = new LinkedHashMap<>();
        db.query(
                "SELECT DISTINCT ON(config_key) config_key,config_value FROM configurations WHERE service_name=? AND environment=? ORDER BY config_key,version DESC", r -> {
                    properties.put(r.getString(1), r.getString(2));
                }, service, environment);
        return Map.of("service", service, "environment", environment, "properties", properties);
    }

    @Override
    public Map<String, Object> add(Configuration c) {
        Integer next = db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM configurations WHERE service_name=? AND environment=? AND config_key=?", Integer.class, c.service(), c.environment(), c.key());
        db.update("INSERT INTO configurations(id,service_name,environment,config_key,config_value,version) VALUES(?,?,?,?,?,?)", UUID.randomUUID(), c.service(), c.environment(), c.key(), c.value(), next);
        return Map.of("version", next, "service", c.service(), "environment", c.environment(), "key", c.key());
    }
}
