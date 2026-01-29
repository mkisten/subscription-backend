package com.mkisten.vacancybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VacancyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacancyBackendApplication.class, args);
    }
}
