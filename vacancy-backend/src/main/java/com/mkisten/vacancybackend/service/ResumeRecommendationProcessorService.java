package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.AiResumeConsumeResponse;
import com.mkisten.vacancybackend.entity.ResumeProfile;
import com.mkisten.vacancybackend.entity.ResumeRecommendationJob;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.repository.ResumeProfileRepository;
import com.mkisten.vacancybackend.repository.ResumeRecommendationJobRepository;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeRecommendationProcessorService {

    private final ResumeRecommendationJobRepository jobRepository;
    private final ResumeProfileRepository resumeProfileRepository;
    private final VacancyRepository vacancyRepository;
    private final VacancyDetailExtractorService vacancyDetailExtractorService;
    private final AiResumePromptService aiResumePromptService;
    private final AiResumeClientService aiResumeClientService;
    private final ResumeAccessService resumeAccessService;

    @Async
    public void processJob(Long jobId, String token) {
        ResumeRecommendationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Resume recommendation job {} not found", jobId);
            return;
        }

        try {
            markRunning(job);

            Vacancy vacancy = vacancyRepository.findByIdAndUserTelegramId(job.getVacancyId(), job.getTelegramId())
                    .orElseThrow(() -> new IllegalArgumentException("Вакансия не найдена"));
            ResumeProfile profile = resumeProfileRepository.findByIdAndTelegramId(job.getResumeProfileId(), job.getTelegramId())
                    .orElseThrow(() -> new IllegalArgumentException("Резюме не найдено"));

            String vacancySnapshot = vacancyDetailExtractorService.buildVacancySnapshot(vacancy);
            job.setVacancySnapshot(vacancySnapshot);
            jobRepository.save(job);

            String prompt = aiResumePromptService.buildPrompt(profile.getExtractedText(), vacancySnapshot);
            String recommendation = aiResumeClientService.generateRecommendation(prompt);
            AiResumeConsumeResponse consumeResponse = resumeAccessService.consume(token);

            job.setRecommendationMarkdown(recommendation);
            job.setUsageSource(parseUsageSource(consumeResponse == null ? null : consumeResponse.getSource()));
            job.setModelName(aiResumeClientService.getConfiguredModel());
            job.setStatus(ResumeRecommendationJob.Status.DONE);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            jobRepository.save(job);
        } catch (Exception ex) {
            log.error("Resume recommendation job {} failed: {}", jobId, ex.getMessage(), ex);
            markFailed(jobId, ex.getMessage());
        }
    }

    @Transactional
    protected void markRunning(ResumeRecommendationJob job) {
        job.setStatus(ResumeRecommendationJob.Status.RUNNING);
        job.setErrorMessage(null);
        jobRepository.save(job);
    }

    @Transactional
    protected void markFailed(Long jobId, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(ResumeRecommendationJob.Status.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(limitError(errorMessage));
            jobRepository.save(job);
        });
    }

    private ResumeRecommendationJob.UsageSource parseUsageSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return ResumeRecommendationJob.UsageSource.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String limitError(String errorMessage) {
        if (errorMessage == null) {
            return "Неизвестная ошибка";
        }
        return errorMessage.length() <= 1200 ? errorMessage : errorMessage.substring(0, 1200);
    }
}
