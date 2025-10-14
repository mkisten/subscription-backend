package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Tag(name = "Bot Management", description = "API для управления Telegram ботом и генерации ссылок")
@SecurityRequirement(name = "JWT")
public class BotManagementController {

    private final TelegramBotService telegramBotService;

    @Operation(
            summary = "Получить ссылку для регистрации",
            description = "Генерирует ссылку для регистрации через Telegram бота"
    )
    @GetMapping("/registration-link")
    public ResponseEntity<?> getRegistrationLink() {
        String link = telegramBotService.generateRegistrationLink();
        return ResponseEntity.ok(Map.of(
                "registrationLink", link,
                "message", "Ссылка для регистрации сгенерирована"
        ));
    }

    @Operation(
            summary = "Получить ссылку для подписки",
            description = "Генерирует ссылку для оформления подписки определенного типа"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ссылка успешно сгенерирована"),
            @ApiResponse(responseCode = "400", description = "Неверный тип подписки")
    })
    @GetMapping("/subscription-link/{plan}")
    public ResponseEntity<?> getSubscriptionLink(
            @Parameter(description = "Тип подписки (MONTHLY, YEARLY, LIFETIME)", required = true)
            @PathVariable String plan) {
        try {
            SubscriptionPlan subscriptionPlan = SubscriptionPlan.valueOf(plan.toUpperCase());
            String link = telegramBotService.generateSubscriptionLink(subscriptionPlan);
            return ResponseEntity.ok(Map.of(
                    "subscriptionLink", link,
                    "plan", subscriptionPlan.getDescription()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid subscription plan",
                    "availablePlans", SubscriptionPlan.values()
            ));
        }
    }

    @Operation(
            summary = "Получить все пригласительные ссылки",
            description = "Генерирует все доступные пригласительные ссылки для бота"
    )
    @GetMapping("/invite-links")
    public ResponseEntity<?> getAllInviteLinks() {
        return ResponseEntity.ok(Map.of(
                "registration", telegramBotService.generateRegistrationLink(),
                "monthly", telegramBotService.generateSubscriptionLink(SubscriptionPlan.MONTHLY),
                "yearly", telegramBotService.generateSubscriptionLink(SubscriptionPlan.YEARLY),
                "lifetime", telegramBotService.generateSubscriptionLink(SubscriptionPlan.LIFETIME)
        ));
    }
}