package com.mkisten.subscriptionbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private boolean success;
    private String message;
    private String transactionId;
    private String clientSecret; // для Stripe
}