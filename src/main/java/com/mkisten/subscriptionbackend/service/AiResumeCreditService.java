package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.dto.AiResumeAccessStatusResponse;
import com.mkisten.subscriptionbackend.dto.AiResumeConsumeResponse;
import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiResumeCreditService {

    public static final int FREE_RECOMMENDATION_LIMIT = 3;
    public static final int PRICE_PER_RECOMMENDATION = 50;

    private final UserService userService;

    @Transactional(readOnly = true)
    public AiResumeAccessStatusResponse getStatus(User user) {
        UserServiceSubscription subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);
        boolean active = userService.isSubscriptionActive(subscription);
        boolean allowed = active && subscription.getSubscriptionPlan() != SubscriptionPlan.TRIAL;
        int freeUsed = normalize(subscription.getResumeRecommendationsFreeUsed());
        int freeRemaining = Math.max(0, FREE_RECOMMENDATION_LIMIT - freeUsed);
        int creditsBalance = normalize(subscription.getResumeRecommendationCredits());

        return new AiResumeAccessStatusResponse(
                active,
                allowed,
                subscription.getSubscriptionPlan().name(),
                FREE_RECOMMENDATION_LIMIT,
                freeUsed,
                freeRemaining,
                creditsBalance,
                PRICE_PER_RECOMMENDATION
        );
    }

    @Transactional
    public AiResumeConsumeResponse consume(User user) {
        UserServiceSubscription subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);
        if (!userService.isSubscriptionActive(subscription)) {
            throw new IllegalStateException("Subscription is not active");
        }
        if (subscription.getSubscriptionPlan() == SubscriptionPlan.TRIAL) {
            throw new IllegalStateException("Feature is not available on TRIAL plan");
        }

        int freeUsed = normalize(subscription.getResumeRecommendationsFreeUsed());
        int creditsBalance = normalize(subscription.getResumeRecommendationCredits());
        String source;

        if (freeUsed < FREE_RECOMMENDATION_LIMIT) {
            freeUsed += 1;
            subscription.setResumeRecommendationsFreeUsed(freeUsed);
            source = "FREE";
        } else if (creditsBalance > 0) {
            creditsBalance -= 1;
            subscription.setResumeRecommendationCredits(creditsBalance);
            source = "PAID";
        } else {
            throw new IllegalStateException("Not enough AI recommendation credits");
        }

        userService.saveServiceSubscription(subscription);
        return new AiResumeConsumeResponse(
                source,
                freeUsed,
                Math.max(0, FREE_RECOMMENDATION_LIMIT - freeUsed),
                creditsBalance
        );
    }

    @Transactional
    public UserServiceSubscription addCredits(Long telegramId, int creditsAmount) {
        if (creditsAmount <= 0) {
            throw new IllegalArgumentException("creditsAmount must be positive");
        }
        User user = userService.findByTelegramId(telegramId);
        UserServiceSubscription subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);
        subscription.setResumeRecommendationCredits(normalize(subscription.getResumeRecommendationCredits()) + creditsAmount);
        return userService.saveServiceSubscription(subscription);
    }

    private int normalize(Integer value) {
        return value == null ? 0 : value;
    }
}
