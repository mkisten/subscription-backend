package com.mkisten.subscriptionbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Test", description = "Тестовые endpoints для проверки работы API")
public class TestController {

    @Operation(
            summary = "Тестовое приветствие",
            description = "Простой тестовый endpoint для проверки работы API"
    )
    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello World!", "status", "OK");
    }

    @Operation(
            summary = "Проверка здоровья сервиса",
            description = "Endpoint для проверки статуса работы сервиса"
    )
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "Subscription Bot");
    }

    @Operation(
            summary = "Публичный endpoint",
            description = "Публичный endpoint, доступный без аутентификации"
    )
    @GetMapping("/public")
    public Map<String, String> publicEndpoint() {
        return Map.of("access", "public", "message", "This endpoint is public");
    }
}