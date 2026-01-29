package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscription.contract.dto.telegram.CreateSessionRequestDto;
import com.mkisten.subscription.contract.dto.telegram.SessionStatusDto;
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

import java.time.OffsetDateTime;
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
    public ResponseEntity<SessionStatusDto> createSession(
            @RequestBody CreateSessionRequestDto request
    ) {
        var session = authSessionService.createSession(request.getDeviceId());

        SessionStatusDto dto = new SessionStatusDto();
        dto.setSessionId(session.getSessionId());
        dto.setDeviceId(session.getDeviceId());
        dto.setStatus(session.getStatus().name());
        dto.setMessage("Session created");
        dto.setToken(null);

        return ResponseEntity.ok(dto);
    }

    /**
     * Проверка статуса авторизации
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<SessionStatusDto> getStatus(
            @PathVariable String sessionId,
            @RequestParam(required = false) String deviceId
    ) {
        var status = authSessionService.checkAuthStatus(sessionId, deviceId);

        SessionStatusDto dto = new SessionStatusDto();
        dto.setSessionId(sessionId);
        dto.setDeviceId(deviceId);
        dto.setStatus(status.getStatus().name());
        dto.setMessage(status.getMessage());
        dto.setToken(status.getJwtToken());

        return ResponseEntity.ok(dto);
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
