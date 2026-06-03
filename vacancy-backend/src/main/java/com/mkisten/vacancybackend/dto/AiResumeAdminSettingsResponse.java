package com.mkisten.vacancybackend.dto;

import com.mkisten.vacancybackend.entity.AiResumeAdminSettings;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiResumeAdminSettingsResponse {
    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutMs;
    private String userAgent;
    private int maxVacancyTextLength;
    private int maxResumeTextLength;
    private String promptTemplate;
    private LocalDateTime updatedAt;
    private Long updatedByTelegramId;

    public static AiResumeAdminSettingsResponse fromRuntime(AiResumeRuntimeSettings runtime) {
        AiResumeAdminSettingsResponse response = new AiResumeAdminSettingsResponse();
        response.setEnabled(runtime.isEnabled());
        response.setBaseUrl(runtime.getBaseUrl());
        response.setApiKey(runtime.getApiKey());
        response.setModel(runtime.getModel());
        response.setTimeoutMs(runtime.getTimeoutMs());
        response.setUserAgent(runtime.getUserAgent());
        response.setMaxVacancyTextLength(runtime.getMaxVacancyTextLength());
        response.setMaxResumeTextLength(runtime.getMaxResumeTextLength());
        response.setPromptTemplate(runtime.getPromptTemplate());
        return response;
    }

    public static AiResumeAdminSettingsResponse fromEntityAndRuntime(
            AiResumeAdminSettings entity,
            AiResumeRuntimeSettings runtime
    ) {
        AiResumeAdminSettingsResponse response = fromRuntime(runtime);
        if (entity != null) {
            response.setUpdatedAt(entity.getUpdatedAt());
            response.setUpdatedByTelegramId(entity.getUpdatedByTelegramId());
        }
        return response;
    }
}
