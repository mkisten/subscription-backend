package com.mkisten.vacancybackend.dto;

import com.mkisten.vacancybackend.entity.ResumeRecommendationJob;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeRecommendationJobResponse {
    private Long id;
    private String vacancyId;
    private Long resumeProfileId;
    private String status;
    private String usageSource;
    private String modelName;
    private String recommendationMarkdown;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public ResumeRecommendationJobResponse(ResumeRecommendationJob job) {
        this.id = job.getId();
        this.vacancyId = job.getVacancyId();
        this.resumeProfileId = job.getResumeProfileId();
        this.status = job.getStatus().name();
        this.usageSource = job.getUsageSource() == null ? null : job.getUsageSource().name();
        this.modelName = job.getModelName();
        this.recommendationMarkdown = job.getRecommendationMarkdown();
        this.errorMessage = job.getErrorMessage();
        this.createdAt = job.getCreatedAt();
        this.completedAt = job.getCompletedAt();
    }
}
