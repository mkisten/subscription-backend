package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.service.TelegramNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "API для управления уведомлениями")
public class NotificationController {

    private final TelegramNotificationService telegramService;

    @Operation(summary = "Отправить тестовое уведомление")
    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> sendTestNotification(
            @RequestHeader("Authorization") String authorization) {
        try {

            String token = authorization.replace("Bearer ", "");
            telegramService.sendTestNotification(token);
            return ResponseEntity.ok(Map.of("message", "Test notification sent successfully"));
        } catch (Exception e) {
            log.error("Error sending test notification: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send test notification",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Отправить кастомное уведомление")
    @PostMapping("/custom")
    public ResponseEntity<Map<String, String>> sendCustomNotification(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> request) {

        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
            }
            String token = authorization.replace("Bearer ", "");
            telegramService.sendTextMessage(token, message);
            return ResponseEntity.ok(Map.of(
                    "message", "Custom notification sent successfully",
                    "messageLength", String.valueOf(message.length())
            ));
        } catch (Exception e) {
            log.error("Error sending custom notification: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send custom notification",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Отправить уведомление об ошибке")
    @PostMapping("/error")
    public ResponseEntity<Map<String, String>> sendErrorNotification(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> request) {

        try {
            String errorMessage = request.get("error");
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Error message is required"));
            }
            String token = authorization.replace("Bearer ", "");
            telegramService.sendErrorNotification(token, errorMessage);
            return ResponseEntity.ok(Map.of("message", "Error notification sent successfully"));
        } catch (Exception e) {
            log.error("Error sending error notification: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send error notification",
                    "message", e.getMessage()
            ));
        }
    }
}
