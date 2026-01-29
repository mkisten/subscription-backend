package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import com.mkisten.vacancybackend.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
@Tag(name = "Session Management", description = "API для управления сессиями пользователя")
public class SessionController {
    private final AuthServiceClient authServiceClient;
    private final UserSettingsService userSettingsService;

    @Operation(summary = "Получить информацию о текущей сессии")
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSessionInfo(
            @RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.replace("Bearer ", "");
            ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
            SubscriptionStatusResponse subscription = userSettingsService.getSubscriptionInfo(token);

            return ResponseEntity.ok(Map.of(
                    "telegramId", profile.getTelegramId(),
                    "user", profile,
                    "subscription", subscription
            ));
        } catch (Exception e) {
            log.error("Failed to get session info: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get session info",
                    "message", e.getMessage()
            ));
        }
    }


    // Сессия "логин/инициализация" — не требуется! Клиент получает токен ОТДЕЛЬНО через auth flow!
    // Если нужен logout, можно реализовать просто "стирание" client-side токена
    @Operation(summary = "Логаут (клиентская очистка токена)")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // Просто клиент должен забыть свой токен, сервер не обрабатывает logout (JWT stateless)
        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }
}
