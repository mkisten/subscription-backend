package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.dto.AiResumeAdminSettingsRequest;
import com.mkisten.vacancybackend.dto.AiResumeAdminSettingsResponse;
import com.mkisten.vacancybackend.dto.AiResumeConnectionTestResponse;
import com.mkisten.vacancybackend.dto.AiResumeRuntimeSettings;
import com.mkisten.vacancybackend.service.AiResumeAdminSettingsService;
import com.mkisten.vacancybackend.service.AiResumeClientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/ai-resume-settings")
@RequiredArgsConstructor
@Tag(name = "AI Resume Admin Settings", description = "Админские настройки AI-рекомендаций по резюме")
public class AiResumeAdminSettingsController {

    private final AiResumeAdminSettingsService aiResumeAdminSettingsService;
    private final AiResumeClientService aiResumeClientService;

    @GetMapping
    public ResponseEntity<AiResumeAdminSettingsResponse> getSettings(
            @RequestHeader("Authorization") String authorization
    ) {
        try {
            return ResponseEntity.ok(aiResumeAdminSettingsService.getAdminSettings(extractToken(authorization)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception ex) {
            log.error("Failed to load AI admin settings: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping
    public ResponseEntity<AiResumeAdminSettingsResponse> updateSettings(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AiResumeAdminSettingsRequest request
    ) {
        try {
            return ResponseEntity.ok(aiResumeAdminSettingsService.saveAdminSettings(extractToken(authorization), request));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception ex) {
            log.error("Failed to update AI admin settings: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/test")
    public ResponseEntity<AiResumeConnectionTestResponse> testSettings(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AiResumeAdminSettingsRequest request
    ) {
        try {
            AiResumeRuntimeSettings settings = aiResumeAdminSettingsService.buildRuntimeSettingsForTest(
                    extractToken(authorization),
                    request
            );
            aiResumeClientService.generateRecommendation(
                    "Ответь ровно словом OK.",
                    settings
            );
            return ResponseEntity.ok(new AiResumeConnectionTestResponse(
                    true,
                    "Подключение и модель работают корректно.",
                    settings.getModel()
            ));
        } catch (IllegalStateException ex) {
            String message = ex.getMessage() == null ? "Не удалось проверить подключение" : ex.getMessage();
            if (message.contains("Недостаточно прав")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.badRequest().body(new AiResumeConnectionTestResponse(
                    false,
                    message,
                    request.getModel()
            ));
        } catch (Exception ex) {
            log.error("Failed to test AI settings: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(new AiResumeConnectionTestResponse(
                    false,
                    ex.getMessage(),
                    request.getModel()
            ));
        }
    }

    private String extractToken(String authorization) {
        return authorization == null ? "" : authorization.replace("Bearer ", "").trim();
    }
}
