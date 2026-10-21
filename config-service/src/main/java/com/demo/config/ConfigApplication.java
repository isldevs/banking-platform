package com.demo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigApplication {
    public static void main(String[] args) {
        System.out.print("\n============================================================\n CONFIG-SERVICE\n============================================================\n\n");
        SpringApplication.run(ConfigApplication.class, args);
    }
}
