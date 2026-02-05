package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;

public interface SubscriptionCalculator {
    boolean calculateSubscriptionActive(UserServiceSubscription subscription);
    int getDaysRemaining(UserServiceSubscription subscription);
}
