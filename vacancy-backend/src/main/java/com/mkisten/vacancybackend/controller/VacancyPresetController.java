package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.dto.SaveVacancyPresetRequest;
import com.mkisten.vacancybackend.dto.VacancyPresetDto;
import com.mkisten.vacancybackend.service.VacancyPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/settings/presets")
@RequiredArgsConstructor
@Tag(name = "Vacancy Presets", description = "API для сохранённых профилей поиска вакансий")
public class VacancyPresetController {

    private final VacancyPresetService vacancyPresetService;

    @Operation(summary = "Получить сохранённые профили поиска")
    @GetMapping
    public ResponseEntity<List<VacancyPresetDto>> getPresets(@RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.replace("Bearer ", "");
            return ResponseEntity.ok(vacancyPresetService.getPresets(token));
        } catch (Exception e) {
            log.error("Error getting vacancy presets: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Сохранить профиль поиска")
    @PostMapping
    public ResponseEntity<?> savePreset(
            @RequestHeader("Authorization") String authorization,
            @RequestBody SaveVacancyPresetRequest request
    ) {
        try {
            String token = authorization.replace("Bearer ", "");
            return ResponseEntity.ok(vacancyPresetService.savePreset(token, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error saving vacancy preset: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Не удалось сохранить профиль поиска."));
        }
    }

    @Operation(summary = "Обновить профиль поиска")
    @PutMapping("/{presetId}")
    public ResponseEntity<?> updatePreset(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String presetId,
            @RequestBody SaveVacancyPresetRequest request
    ) {
        try {
            String token = authorization.replace("Bearer ", "");
            return ResponseEntity.ok(vacancyPresetService.updatePreset(token, presetId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating vacancy preset: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Не удалось обновить профиль поиска."));
        }
    }

    @Operation(summary = "Удалить профиль поиска")
    @DeleteMapping("/{presetId}")
    public ResponseEntity<?> deletePreset(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String presetId
    ) {
        try {
            String token = authorization.replace("Bearer ", "");
            vacancyPresetService.deletePreset(token, presetId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error deleting vacancy preset: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Не удалось удалить профиль поиска."));
        }
    }
}
