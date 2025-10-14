package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import lombok.Data;

@Data
public class ExtendSubscriptionRequest {
    private String email;
    private int days;
    private SubscriptionPlan plan;
}
