package com.mkisten.vacancybackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateResumeRecommendationRequest {
    @NotBlank
    private String vacancyId;

    private Long resumeProfileId;
}
