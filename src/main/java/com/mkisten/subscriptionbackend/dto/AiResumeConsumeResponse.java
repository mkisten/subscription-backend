package com.mkisten.subscriptionbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResumeConsumeResponse {
    private String source;
    private int freeUsed;
    private int freeRemaining;
    private int creditsBalance;
}
