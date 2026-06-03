package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class AiResumeAdminSettingsRequest {
    private Boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer timeoutMs;
    private String userAgent;
    private Integer maxVacancyTextLength;
    private Integer maxResumeTextLength;
    private String promptTemplate;
}
