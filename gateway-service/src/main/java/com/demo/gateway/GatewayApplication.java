package com.demo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        System.out.print("\n============================================================\n GATEWAY-SERVICE\n============================================================\n\n");
        SpringApplication.run(GatewayApplication.class, args);
    }
}
