package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class AiResumeAccessStatusResponse {
    private boolean subscriptionActive;
    private boolean allowed;
    private String subscriptionPlan;
    private int freeLimit;
    private int freeUsed;
    private int freeRemaining;
    private int creditsBalance;
    private int pricePerRecommendation;
}
