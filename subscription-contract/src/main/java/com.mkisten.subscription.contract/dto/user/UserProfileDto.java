package com.mkisten.subscription.contract.dto.user;

import com.mkisten.subscription.contract.enums.ServiceCodeDto;
import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Объединяет твой прежний ProfileResponse (vacancy) и AuthResponse (subscription).
 */
@Data
public class UserProfileDto {

    private Long telegramId;

    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phone;
    private String login;

    private LocalDate subscriptionEndDate;
    private SubscriptionPlanDto subscriptionPlan;
    private ServiceCodeDto serviceCode;

    // именно isActive – чтобы JSON остался совместимым с тем,
    // что у тебя уже есть (поле isActive).
    private Boolean isActive;

    private Integer daysRemaining;
    private Boolean trialUsed;

    // оставляем как String, чтобы не тащить enum роли в контракт
    private String role;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
