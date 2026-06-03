package com.mkisten.vacancybackend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiResumeRuntimeSettings {
    boolean enabled;
    String baseUrl;
    String apiKey;
    String model;
    int timeoutMs;
    String userAgent;
    int maxVacancyTextLength;
    int maxResumeTextLength;
    String promptTemplate;
}
