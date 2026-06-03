package com.mkisten.vacancybackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resume_recommendation_jobs", indexes = {
        @Index(name = "idx_resume_jobs_telegram_id", columnList = "telegram_id"),
        @Index(name = "idx_resume_jobs_status", columnList = "telegram_id,status"),
        @Index(name = "idx_resume_jobs_vacancy", columnList = "vacancy_id,telegram_id")
})
@Getter
@Setter
public class ResumeRecommendationJob {

    public enum Status {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }

    public enum UsageSource {
        FREE,
        PAID
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "vacancy_id", nullable = false, length = 255)
    private String vacancyId;

    @Column(name = "resume_profile_id", nullable = false)
    private Long resumeProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_source", length = 16)
    private UsageSource usageSource;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "vacancy_snapshot", columnDefinition = "TEXT")
    private String vacancySnapshot;

    @Column(name = "recommendation_markdown", columnDefinition = "TEXT")
    private String recommendationMarkdown;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
