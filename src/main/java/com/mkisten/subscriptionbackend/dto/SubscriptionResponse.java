package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import lombok.Data;
import java.time.LocalDate;

@Data
public class SubscriptionResponse {
    private String email;
    private LocalDate subscriptionEndDate;
    private SubscriptionPlan subscriptionPlan;
    private boolean subscriptionActive;

    public SubscriptionResponse(String email, LocalDate subscriptionEndDate,
                                SubscriptionPlan plan, boolean subscriptionActive) {
        this.email = email;
        this.subscriptionEndDate = subscriptionEndDate;
        this.subscriptionPlan = plan;
        this.subscriptionActive = subscriptionActive;
    }
}
