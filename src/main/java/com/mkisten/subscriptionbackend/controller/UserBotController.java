package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.TelegramBotService;
import com.mkisten.subscriptionbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Tag(name = "User Bot Notifications", description = "API для отправки сообщений пользователю через Telegram бот")
public class UserBotController {

    private final TelegramBotService telegramBotService;
    private final UserService userService;

    @Operation(summary = "Отправить уведомление самому себе")
    @PostMapping("/notify")
    public ResponseEntity<?> sendNotificationToSelf(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> request
    ) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Message is required"
                ));
            }

            Long telegramId = currentUser.getTelegramId();
            if (telegramId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Your profile is not linked to Telegram"
                ));
            }

            telegramBotService.sendTextMessageToUser(telegramId, message);

            return ResponseEntity.ok(Map.of(
                    "message", "Notification sent",
                    "telegramId", telegramId,
                    "sentAt", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error sending notification to self", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send notification",
                    "message", e.getMessage()
            ));
        }
    }
}
