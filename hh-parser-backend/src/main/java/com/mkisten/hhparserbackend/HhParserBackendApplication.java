package com.mkisten.hhparserbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HhParserBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HhParserBackendApplication.class, args);
    }
}
