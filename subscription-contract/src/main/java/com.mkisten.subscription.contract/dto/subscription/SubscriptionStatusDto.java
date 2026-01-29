package com.mkisten.subscription.contract.dto.subscription;

import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import lombok.Data;

import java.time.LocalDate;

/**
 * То, чем раньше был SubscriptionStatusResponse в обоих сервисах.
 */
@Data
public class SubscriptionStatusDto {

    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
    private String email;

    private LocalDate subscriptionEndDate;
    private SubscriptionPlanDto subscriptionPlan;

    private Boolean active;
    private Long daysRemaining;
    private Boolean trialUsed;

    private String role;
}
