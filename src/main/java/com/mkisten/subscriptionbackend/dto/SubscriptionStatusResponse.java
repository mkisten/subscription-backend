// SubscriptionStatusResponse.java
package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ со статусом подписки")
public class SubscriptionStatusResponse {
    @Schema(description = "Telegram ID пользователя")
    private Long telegramId;

    @Schema(description = "Имя пользователя")
    private String firstName;

    @Schema(description = "Фамилия пользователя")
    private String lastName;

    @Schema(description = "Username в Telegram")
    private String username;

    @Schema(description = "Email пользователя")
    private String email;

    @Schema(description = "Дата окончания подписки")
    private LocalDate subscriptionEndDate;

    @Schema(description = "Тип подписки")
    private SubscriptionPlan subscriptionPlan;

    @Schema(description = "Активна ли подписка")
    private boolean isActive;

    @Schema(description = "Осталось дней подписки")
    private long daysRemaining;

    @Schema(description = "Использован ли trial период")
    private boolean trialUsed;

    @Schema(description = "Роль пользователя")
    private String role;
}