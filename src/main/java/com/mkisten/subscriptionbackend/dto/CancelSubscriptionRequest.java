package com.mkisten.subscriptionbackend.dto;

import lombok.Data;

@Data
public class CancelSubscriptionRequest {
    private String email;
}
