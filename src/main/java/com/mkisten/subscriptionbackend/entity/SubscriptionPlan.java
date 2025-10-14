package com.mkisten.subscriptionbackend.entity;

import lombok.Getter;

@Getter
public enum SubscriptionPlan {
    TRIAL("Пробный период", 0, 7),
    MONTHLY("Месячная подписка", 200, 30),
    YEARLY("Годовая подписка", 1700, 365),
    LIFETIME("Постоянный доступ", 3000, 36500);

    private final String description;
    private final double price;
    private final int days;

    SubscriptionPlan(String description, double price, int days) {
        this.description = description;
        this.price = price;
        this.days = days;
    }
}