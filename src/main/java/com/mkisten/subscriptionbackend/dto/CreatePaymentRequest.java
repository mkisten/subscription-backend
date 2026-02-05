package com.mkisten.subscriptionbackend.dto;

import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import lombok.Data;

@Data
public class CreatePaymentRequest {

    private SubscriptionPlan plan;
    private Integer months;
    private ServiceCode service;
}
