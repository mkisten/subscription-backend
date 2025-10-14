package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.CreateSessionRequest;
import com.mkisten.subscriptionbackend.entity.AuthSession;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import com.mkisten.subscriptionbackend.service.AuthSessionService;
import com.mkisten.subscriptionbackend.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/telegram-auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TelegramAuthController {

    private final TelegramBotService telegramBotService;
    private final AuthSessionService authSessionService;
    private final JwtUtil jwtUtil;

    @Value("${telegram.bot.username}")
    private String botUsername;

    /**
     * Создание сессии для авторизации через Telegram бота
     */
    @PostMapping("/create-session")
    public ResponseEntity<?> createAuthSession(@RequestBody CreateSessionRequest request) {
        try {
            log.info("=== CREATE SESSION REQUEST ===");
            log.info("Device ID: {}", request.getDeviceId());

            String deviceId = request.getDeviceId();
            if (deviceId == null || deviceId.trim().isEmpty()) {
                deviceId = "web-" + System.currentTimeMillis();
                log.info("Generated device ID: {}", deviceId);
            }

            AuthSession session = telegramBotService.createAuthSession(deviceId);

            log.info("=== SESSION CREATED ===");
            log.info("Session ID: {}", session.getSessionId());
            log.info("Device ID: {}", session.getDeviceId());
            log.info("Status: {}", session.getStatus());

            // Проверяем, что сессия действительно сохранена
            Optional<AuthSession> verifiedSession = authSessionService.findBySessionId(session.getSessionId());
            if (verifiedSession.isEmpty()) {
                log.error("CRITICAL: Session not found immediately after creation: {}", session.getSessionId());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Session creation failed"));
            }

            // Генерируем deep link для Telegram бота
            String deepLink = telegramBotService.generateAuthDeepLink(session.getSessionId(), deviceId);

            log.info("=== DEEP LINK GENERATED ===");
            log.info("Deep link: {}", deepLink);

            // Возвращаем объект с deepLink
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", session.getSessionId());
            response.put("deviceId", session.getDeviceId());
            response.put("status", session.getStatus());
            response.put("createdAt", session.getCreatedAt());
            response.put("expiresAt", session.getExpiresAt());
            response.put("deepLink", deepLink);
            response.put("botUsername", botUsername);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating auth session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Проверка статуса авторизации
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> checkAuthStatus(
            @PathVariable String sessionId,
            @RequestParam(required = false) String deviceId) {
        try {
            log.info("=== CHECK AUTH STATUS ===");
            log.info("Session ID: {}", sessionId);
            log.info("Device ID: {}", deviceId);

            String actualDeviceId = (deviceId != null && !deviceId.trim().isEmpty())
                    ? deviceId
                    : "unknown";

            var status = telegramBotService.checkAuthStatus(sessionId, actualDeviceId);

            log.info("=== STATUS RESULT ===");
            log.info("Status: {}", status.getStatus());
            log.info("Message: {}", status.getMessage());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error checking auth status for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Тестовое завершение авторизации (для отладки)
     */
    @PostMapping("/test-complete")
    public ResponseEntity<?> testCompleteAuth(
            @RequestParam String sessionId,
            @RequestParam Long telegramId) {
        try {
            log.info("=== TEST COMPLETE AUTH ===");
            log.info("Session ID: {}, Telegram ID: {}", sessionId, telegramId);

            // Генерируем JWT токен используя напрямую jwtUtil
            String jwtToken = jwtUtil.generateToken(telegramId);

            // Завершаем сессию
            AuthSession completedSession = authSessionService.completeAuthSession(sessionId, telegramId, jwtToken);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwtToken);
            response.put("telegramId", telegramId);
            response.put("sessionId", completedSession.getSessionId());
            response.put("status", "COMPLETED");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in test complete auth", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Test auth failed: " + e.getMessage()));
        }
    }

    /**
     * Отладочная информация о сессиях
     */
    @GetMapping("/debug/sessions")
    public ResponseEntity<?> debugSessions() {
        try {
            Map<String, Object> debugInfo = authSessionService.getDebugInfo();
            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Debug error: " + e.getMessage()));
        }
    }
}