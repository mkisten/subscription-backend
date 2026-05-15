package com.mkisten.getmatchparserbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GetmatchParserBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GetmatchParserBackendApplication.class, args);
    }
}
