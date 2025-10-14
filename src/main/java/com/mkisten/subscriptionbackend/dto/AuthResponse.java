package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private LocalDate subscriptionEndDate;
    private Boolean isActive;
    private SubscriptionPlan subscriptionPlan;
    private Boolean trialUsed;
    private Integer daysRemaining;
    private String role;
}