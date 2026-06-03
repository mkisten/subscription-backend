package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.CreateResumeRecommendationRequest;
import com.mkisten.vacancybackend.dto.ResumeRecommendationJobResponse;
import com.mkisten.vacancybackend.dto.ResumeWorkspaceResponse;
import com.mkisten.vacancybackend.entity.ResumeProfile;
import com.mkisten.vacancybackend.entity.ResumeRecommendationJob;
import com.mkisten.vacancybackend.repository.ResumeRecommendationJobRepository;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeRecommendationService {

    private final ResumeAccessService resumeAccessService;
    private final ResumeProfileService resumeProfileService;
    private final ResumeRecommendationJobRepository jobRepository;
    private final ResumeRecommendationProcessorService processorService;
    private final VacancyRepository vacancyRepository;
    private final UserSettingsService userSettingsService;

    @Transactional(readOnly = true)
    public ResumeWorkspaceResponse getWorkspace(String token) {
        return new ResumeWorkspaceResponse(
                resumeAccessService.getAccessStatus(token),
                resumeProfileService.listProfiles(token),
                resumeProfileService.getActiveProfile(token),
                listRecentJobs(token)
        );
    }

    @Transactional
    public ResumeRecommendationJobResponse createRecommendation(String token, CreateResumeRecommendationRequest request) {
        resumeAccessService.assertRecommendationAvailable(token);
        Long telegramId = userSettingsService.getTelegramId(token);
        vacancyRepository.findByIdAndUserTelegramId(request.getVacancyId(), telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Вакансия не найдена"));
        ResumeProfile profile = resumeProfileService.requireProfile(token, request.getResumeProfileId());

        ResumeRecommendationJob job = new ResumeRecommendationJob();
        job.setTelegramId(telegramId);
        job.setVacancyId(request.getVacancyId());
        job.setResumeProfileId(profile.getId());
        job.setStatus(ResumeRecommendationJob.Status.PENDING);
        ResumeRecommendationJob savedJob = jobRepository.save(job);
        processorService.processJob(savedJob.getId(), token);
        return new ResumeRecommendationJobResponse(savedJob);
    }

    @Transactional(readOnly = true)
    public List<ResumeRecommendationJobResponse> listRecentJobs(String token) {
        Long telegramId = userSettingsService.getTelegramId(token);
        return jobRepository.findByTelegramIdOrderByCreatedAtDesc(telegramId).stream()
                .limit(20)
                .map(ResumeRecommendationJobResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeRecommendationJobResponse getJob(String token, Long jobId) {
        Long telegramId = userSettingsService.getTelegramId(token);
        ResumeRecommendationJob job = jobRepository.findByIdAndTelegramId(jobId, telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Рекомендация не найдена"));
        return new ResumeRecommendationJobResponse(job);
    }
}
