package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.CreateSessionRequest;
import com.mkisten.vacancybackend.dto.SessionStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/telegram-auth")
@RequiredArgsConstructor
public class VacancyTelegramAuthController {
    private final AuthServiceClient authServiceClient;

    /** Создать новую Telegram-auth сессию */
    @PostMapping("/create-session")
    public ResponseEntity<?> createAuthSession(@RequestBody CreateSessionRequest req) {
        return ResponseEntity.ok(authServiceClient.createSession(req));
    }

    /** Проверить статус Telegram-auth сессии */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<SessionStatusResponse> checkStatus(
            @PathVariable String sessionId,
            @RequestParam(required = false) String deviceId
    ) {
        return ResponseEntity.ok(authServiceClient.getSessionStatus(sessionId, deviceId));
    }
}
