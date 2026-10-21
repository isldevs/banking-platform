package com.demo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        System.out.print("\n============================================================\n NOTIFICATION-SERVICE\n============================================================\n\n");
        SpringApplication.run(Application.class, args);
    }
}
