package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import lombok.Data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
public class UserResponse {
    private String email;
    private LocalDate subscriptionEndDate;
    private SubscriptionPlan subscriptionPlan;
    private boolean subscriptionActive;
    private long daysRemaining;

    public UserResponse(String email, LocalDate subscriptionEndDate,
                        SubscriptionPlan plan, boolean subscriptionActive) {
        this.email = email;
        this.subscriptionEndDate = subscriptionEndDate;
        this.subscriptionPlan = plan;
        this.subscriptionActive = subscriptionActive;
        this.daysRemaining = calculateDaysRemaining();
    }

    private long calculateDaysRemaining() {
        if (subscriptionEndDate == null) return 0;
        return ChronoUnit.DAYS.between(LocalDate.now(), subscriptionEndDate);
    }
}
