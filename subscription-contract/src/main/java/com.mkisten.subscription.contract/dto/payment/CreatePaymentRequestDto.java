package com.mkisten.subscription.contract.dto.payment;

import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import lombok.Data;

/**
 * Запрос на создание платежа.
 */
@Data
public class CreatePaymentRequestDto {
    private SubscriptionPlanDto plan;
    private Integer months;
}
