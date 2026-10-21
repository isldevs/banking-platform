package com.demo.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuditApplication {
    public static void main(String[] args) {
        System.out.print("\n============================================================\n AUDIT-SERVICE\n============================================================\n\n");
        SpringApplication.run(AuditApplication.class, args);
    }
}
