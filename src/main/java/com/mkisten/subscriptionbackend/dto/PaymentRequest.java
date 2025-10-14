package com.mkisten.subscriptionbackend.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private String plan; // MONTHLY, YEARLY, LIFETIME
    private String paymentMethod; // CARD, TELEGRAM_STARS, etc.
}