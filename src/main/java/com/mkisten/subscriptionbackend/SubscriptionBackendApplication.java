package com.mkisten.subscriptionbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SubscriptionBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionBackendApplication.class, args);
    }

}
