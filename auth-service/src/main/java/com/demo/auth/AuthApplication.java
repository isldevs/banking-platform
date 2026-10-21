package com.demo.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthApplication {
    public static void main(String[] args) {
        System.out.print("\n============================================================\n AUTH-SERVICE\n============================================================\n\n");
        SpringApplication.run(AuthApplication.class, args);
    }
}
