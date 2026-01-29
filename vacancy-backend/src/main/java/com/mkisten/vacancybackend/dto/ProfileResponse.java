package com.mkisten.vacancybackend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProfileResponse {

    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phone;
    private LocalDate subscriptionEndDate;
    private String subscriptionPlan;
    private Boolean isActive;
    private Integer daysRemaining;
    private Boolean trialUsed;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
