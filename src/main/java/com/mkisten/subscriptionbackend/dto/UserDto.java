package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

// DTO пользователя
@Data
@Builder
public class UserDto {
    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
    private SubscriptionPlan subscriptionPlan;
    private LocalDate subscriptionEndDate;
    private boolean isActive;
    private Integer daysRemaining;
}
