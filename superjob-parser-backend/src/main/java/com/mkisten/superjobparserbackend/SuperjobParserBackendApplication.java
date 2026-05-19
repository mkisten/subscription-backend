package com.mkisten.superjobparserbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SuperjobParserBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SuperjobParserBackendApplication.class, args);
    }
}
