package com.demo.gateway.infrastructure.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitWebConfig implements WebMvcConfigurer {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public RateLimitWebConfig(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor(rateLimitService, properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor()).addPathPatterns("/api/**");
    }
}
