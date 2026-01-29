package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.dto.VacancyResponse;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.entity.VacancyStatus;
import com.mkisten.vacancybackend.service.UserSettingsService;
import com.mkisten.vacancybackend.service.VacancyService;
import com.mkisten.vacancybackend.service.VacancySmartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/vacancies")
@RequiredArgsConstructor
@Tag(name = "Vacancies", description = "API для управления вакансиями")
public class VacancyController {

    private final VacancyService vacancyService;
    private final VacancySmartService vacancySmartService;
    private final UserSettingsService userSettingsService;

    @Operation(summary = "Поиск вакансий с учетом пользовательских настроек")
    @PostMapping("/search")
    public ResponseEntity<List<VacancyResponse>> searchVacancies(
            @RequestHeader("Authorization") String authorization,
            @RequestBody SearchRequest request) {
        try {
            String token = authorization.replace("Bearer ", "");
            Long userTelegramId = userSettingsService.getSettings(token).getTelegramId();
            log.info("Search request: {}", request.getQuery());

            // Выполняем поиск (функция включает сохранение и отправку)
            List<Vacancy> foundVacancies = vacancySmartService.searchWithUserSettings(request, token, userTelegramId);

            List<VacancyResponse> response = foundVacancies.stream()
                    .map(VacancyResponse::new)
                    .collect(Collectors.toList());

            log.info("Search completed. Found: {}", foundVacancies.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching vacancies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Получить вакансии пользователя")
    @GetMapping
    public ResponseEntity<List<VacancyResponse>> getUserVacancies(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) VacancyStatus status) {
        try {
            String token = authorization.replace("Bearer ", "");
            List<Vacancy> vacancies = vacancyService.getUserVacancies(token, status);
            List<VacancyResponse> response = vacancies.stream()
                    .map(VacancyResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting vacancies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> addVacanciesBatch(
            @RequestHeader("Authorization") String authorization,
            @RequestBody List<Vacancy> vacancies) {
        try {
            String token = authorization.replace("Bearer ", "");
            log.info("Получен запрос на добавление {} вакансий", vacancies.size());

            List<Vacancy> savedVacancies = vacancyService.saveVacancies(token, vacancies);
            int addedCount = savedVacancies.size();
            int skippedCount = vacancies.size() - addedCount;

            Map<String, Object> response = new HashMap<>();
            response.put("added", addedCount);
            response.put("skipped", skippedCount);
            response.put("totalProcessed", vacancies.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при добавлении вакансий: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Ошибка при добавлении вакансий");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @Operation(summary = "Пометить вакансию просмотренной")
    @PostMapping("/{vacancyId}/mark-viewed")
    public ResponseEntity<Void> markAsViewed(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String vacancyId) {
        try {
            String token = authorization.replace("Bearer ", "");
            vacancyService.markAsViewed(token, vacancyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking vacancy as viewed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Пометить несколько вакансий просмотренными")
    @PostMapping("/mark-multiple-viewed")
    public ResponseEntity<Void> markMultipleAsViewed(
            @RequestHeader("Authorization") String authorization,
            @RequestBody List<String> vacancyIds) {
        try {
            String token = authorization.replace("Bearer ", "");
            vacancyService.markMultipleAsViewed(token, vacancyIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking multiple vacancies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Удалить вакансию")
    @DeleteMapping("/{vacancyId}")
    public ResponseEntity<Void> deleteVacancy(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String vacancyId) {
        try {
            String token = authorization.replace("Bearer ", "");
            vacancyService.deleteVacancy(token, vacancyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting vacancy: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Получить количество новых вакансий")
    @GetMapping("/count/new")
    public ResponseEntity<Long> getNewVacanciesCount(
            @RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.replace("Bearer ", "");
            Long count = vacancyService.getNewVacanciesCount(token);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("Error getting new vacancies count: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
