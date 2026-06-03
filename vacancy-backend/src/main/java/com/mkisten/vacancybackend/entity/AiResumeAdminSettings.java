package com.mkisten.vacancybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_resume_admin_settings")
@Getter
@Setter
public class AiResumeAdminSettings {

    @Id
    private Long id = 1L;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "api_key", length = 2000)
    private String apiKey;

    @Column(name = "model", length = 255)
    private String model;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "max_vacancy_text_length")
    private Integer maxVacancyTextLength;

    @Column(name = "max_resume_text_length")
    private Integer maxResumeTextLength;

    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(name = "updated_by_telegram_id")
    private Long updatedByTelegramId;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
