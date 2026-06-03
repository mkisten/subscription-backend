package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.AiResumeAccessStatusResponse;
import com.mkisten.vacancybackend.dto.AiResumeConsumeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResumeAccessService {

    private final AuthServiceClient authServiceClient;

    public AiResumeAccessStatusResponse getAccessStatus(String token) {
        return authServiceClient.getAiResumeStatus(token);
    }

    public void assertPaidFeatureAvailable(String token) {
        AiResumeAccessStatusResponse status = getAccessStatus(token);
        if (status == null || !status.isAllowed()) {
            throw new IllegalStateException("AI-рекомендации доступны только для активных платных тарифов");
        }
    }

    public void assertRecommendationAvailable(String token) {
        AiResumeAccessStatusResponse status = getAccessStatus(token);
        if (status == null || !status.isAllowed()) {
            throw new IllegalStateException("AI-рекомендации доступны только для активных платных тарифов");
        }
        if (status.getFreeRemaining() <= 0 && status.getCreditsBalance() <= 0) {
            throw new IllegalStateException("Бесплатные рекомендации закончились. Пополните баланс рекомендаций.");
        }
    }

    public AiResumeConsumeResponse consume(String token) {
        return authServiceClient.consumeAiResume(token);
    }
}
