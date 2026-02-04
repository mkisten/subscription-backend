package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "API для управления настройками пользователя")
public class UserSettingsController {

    private final UserSettingsService settingsService;
    private final AuthServiceClient authServiceClient;

    @Operation(summary = "Получить настройки пользователя")
    @GetMapping
    public ResponseEntity<UserSettings> getSettings(@RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.replace("Bearer ", "");
            UserSettings settings = settingsService.getSettings(token);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error getting settings: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Получить настройки пользователя (админ)")
    @GetMapping("/admin/{telegramId}")
    public ResponseEntity<UserSettings> getUserSettingsForAdmin(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long telegramId
    ) {
        try {
            String token = authorization.replace("Bearer ", "");
            ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
            if (profile == null || !isAdminRole(profile.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            UserSettings settings = settingsService.getSettingsByTelegramId(telegramId);
            return ResponseEntity.ok(settings);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting user settings for admin: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Обновить настройки пользователя")
    @PutMapping
    public ResponseEntity<UserSettings> updateSettings(
            @RequestHeader("Authorization") String authorization,
            @RequestBody UserSettings settings) {
        try {
            String token = authorization.replace("Bearer ", "");
            UserSettings updated = settingsService.updateSettings(token, settings);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating settings: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Настройка автообновления")
    @PostMapping("/auto-update")
    public ResponseEntity<Map<String, String>> setupAutoUpdate(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request) {
        try {
            String token = authorization.replace("Bearer ", "");

            Boolean enabled = (Boolean) request.get("enabled");
            Integer intervalMinutes = (Integer) request.get("intervalMinutes");

            if (enabled == null || intervalMinutes == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Both 'enabled' and 'intervalMinutes' are required"
                ));
            }
            settingsService.setupAutoUpdate(token, enabled, intervalMinutes);
            return ResponseEntity.ok(Map.of(
                    "message", "Auto-update settings updated successfully",
                    "enabled", enabled.toString(),
                    "intervalMinutes", intervalMinutes.toString()
            ));

        } catch (Exception e) {
            log.error("Error setting up auto-update: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to update auto-update settings",
                    "message", e.getMessage()
            ));
        }
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "MODERATOR".equals(role);
    }
}
