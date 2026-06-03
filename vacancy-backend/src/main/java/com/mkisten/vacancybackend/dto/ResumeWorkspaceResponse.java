package com.mkisten.vacancybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeWorkspaceResponse {
    private AiResumeAccessStatusResponse access;
    private List<ResumeProfileResponse> profiles;
    private ResumeProfileResponse activeProfile;
    private List<ResumeRecommendationJobResponse> recentJobs;
}
