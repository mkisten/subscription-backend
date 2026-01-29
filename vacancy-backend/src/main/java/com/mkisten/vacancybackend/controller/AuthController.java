package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth") // Снаружи будет /api/auth/...
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Прокси к сервису авторизации")
public class AuthController {

    private final AuthServiceClient authServiceClient;

    @Operation(summary = "Получить токен по Telegram ID (через сервис авторизации)")
    @GetMapping("/token")
    public ResponseEntity<TokenResponse> getTokenByTelegramId(@RequestParam("telegramId") Long telegramId) {
        TokenResponse response = authServiceClient.getTokenByTelegramId(telegramId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Получить токен по Telegram ID (через сервис авторизации)")
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> getToken(@RequestBody AuthRequest request) {
        TokenResponse response = authServiceClient.getTokenByTelegramId(request.getTelegramId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Проксировать валидацию токена")
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        boolean isValid = authServiceClient.validateToken(token);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }

    @Operation(summary = "Обновление токена (refresh - через auth-сервис)")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        TokenResponse refreshed = authServiceClient.refreshToken(token);
        return ResponseEntity.ok(refreshed);
    }

    @Operation(summary = "Получить информацию о токене (валидность, telegramId, тип)")
    @GetMapping("/token-info")
    public ResponseEntity<Map<String, Object>> getTokenInfo(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        boolean isValid = authServiceClient.validateToken(token);

        Long telegramId = null;
        if (isValid) {
            ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
            if (profile != null) {
                telegramId = profile.getTelegramId();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("valid", isValid);
        result.put("tokenType", "JWT");
        if (telegramId != null) {
            result.put("telegramId", telegramId);
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Получить профиль пользователя по токену (прокси)")
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "Обновить профиль пользователя (прокси)")
    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ProfileResponse updateDto) {
        String token = authHeader.replace("Bearer ", "");
        ProfileResponse updated = authServiceClient.updateProfile(token, updateDto);
        return ResponseEntity.ok(updated);
    }
}
