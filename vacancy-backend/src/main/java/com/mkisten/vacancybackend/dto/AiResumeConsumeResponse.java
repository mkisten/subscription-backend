package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class AiResumeConsumeResponse {
    private String source;
    private int freeUsed;
    private int freeRemaining;
    private int creditsBalance;
}
