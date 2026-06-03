package com.mkisten.subscriptionbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
