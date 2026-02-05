package com.mkisten.subscription.contract.dto.payment;

import com.mkisten.subscription.contract.enums.ServiceCodeDto;
import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO для отдачи платежа наружу (вместо entity Payment).
 */
@Data
public class PaymentResponseDto {

    private Long id;
    private Long telegramId;

    private Double amount;
    private SubscriptionPlanDto plan;
    private Integer months;
    private ServiceCodeDto service;

    /**
     * Статус из Payment.PaymentStatus.name()
     */
    private String status;

    private String phoneNumber;

    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;

    private String adminNotes;
}
