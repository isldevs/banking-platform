package com.demo.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountApplication {
    public static void main(String[] args) {
        System.out.print("\n============================================================\n ACCOUNT-SERVICE\n============================================================\n\n");
        SpringApplication.run(AccountApplication.class, args);
    }
}
