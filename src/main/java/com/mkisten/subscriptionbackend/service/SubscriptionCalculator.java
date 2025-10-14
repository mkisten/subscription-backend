package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.User;

public interface SubscriptionCalculator {
    boolean calculateSubscriptionActive(User user);
    int getDaysRemaining(User user);
}
