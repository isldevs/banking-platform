package com.demo.config.service;

import com.demo.config.entity.Configuration;

import java.util.Map;

public interface ConfigService {
    Map<String, Object> read(String service, String environment);

    Map<String, Object> add(Configuration c);
}
