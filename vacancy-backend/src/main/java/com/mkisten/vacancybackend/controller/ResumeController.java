package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.dto.CreateResumeRecommendationRequest;
import com.mkisten.vacancybackend.dto.ResumeProfileResponse;
import com.mkisten.vacancybackend.dto.ResumeRecommendationJobResponse;
import com.mkisten.vacancybackend.dto.ResumeWorkspaceResponse;
import com.mkisten.vacancybackend.service.ResumeProfileService;
import com.mkisten.vacancybackend.service.ResumeRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeProfileService resumeProfileService;
    private final ResumeRecommendationService resumeRecommendationService;

    @GetMapping("/workspace")
    public ResponseEntity<ResumeWorkspaceResponse> getWorkspace(@RequestHeader("Authorization") String authorization) {
        try {
            return ResponseEntity.ok(resumeRecommendationService.getWorkspace(extractToken(authorization)));
        } catch (Exception ex) {
            log.error("Failed to load resume workspace: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadResume(
            @RequestHeader("Authorization") String authorization,
            @RequestPart("file") MultipartFile file
    ) {
        try {
            ResumeProfileResponse response = resumeProfileService.upload(extractToken(authorization), file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(ex, "Не удалось загрузить резюме")));
        } catch (IOException ex) {
            log.error("Failed to upload resume: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of("message", "Не удалось загрузить резюме"));
        }
    }

    @PostMapping("/{profileId}/activate")
    public ResponseEntity<?> activateProfile(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long profileId
    ) {
        try {
            return ResponseEntity.ok(resumeProfileService.activate(extractToken(authorization), profileId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(ex, "Не удалось выбрать резюме")));
        } catch (Exception ex) {
            log.error("Failed to activate resume profile {}: {}", profileId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<?> deleteProfile(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long profileId
    ) {
        try {
            resumeProfileService.delete(extractToken(authorization), profileId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(ex, "Не удалось удалить резюме")));
        } catch (Exception ex) {
            log.error("Failed to delete resume profile {}: {}", profileId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<ResumeRecommendationJobResponse>> listJobs(
            @RequestHeader("Authorization") String authorization
    ) {
        try {
            return ResponseEntity.ok(resumeRecommendationService.listRecentJobs(extractToken(authorization)));
        } catch (Exception ex) {
            log.error("Failed to list recommendation jobs: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/recommendations/{jobId}")
    public ResponseEntity<?> getJob(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long jobId
    ) {
        try {
            return ResponseEntity.ok(resumeRecommendationService.getJob(extractToken(authorization), jobId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(ex, "Рекомендация не найдена")));
        } catch (Exception ex) {
            log.error("Failed to get recommendation job {}: {}", jobId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/recommendations")
    public ResponseEntity<?> createRecommendation(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateResumeRecommendationRequest request
    ) {
        try {
            return ResponseEntity.ok(resumeRecommendationService.createRecommendation(extractToken(authorization), request));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", safeMessage(ex, "Не удалось запустить AI-рекомендацию")));
        } catch (Exception ex) {
            log.error("Failed to create recommendation: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of("message", "Не удалось запустить AI-рекомендацию"));
        }
    }

    private String extractToken(String authorization) {
        return authorization == null ? "" : authorization.replace("Bearer ", "").trim();
    }

    private String safeMessage(Exception ex, String fallback) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? fallback : ex.getMessage();
    }
}
