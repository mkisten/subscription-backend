package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class SubscriptionCalculatorImpl implements SubscriptionCalculator {
    @Override
    public boolean calculateSubscriptionActive(UserServiceSubscription subscription) {
        if (subscription == null || subscription.getSubscriptionEndDate() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate endDate = subscription.getSubscriptionEndDate();
        log.info("=== Расчет статуса подписки ===");
        log.info("Subscription ID: {}", subscription.getId());
        log.info("Сегодня: {}", today);
        log.info("Дата окончания: {}", endDate);
        log.info("today.isAfter(endDate): {}", today.isAfter(endDate));
        log.info("!today.isAfter(endDate): {}", !today.isAfter(endDate));
        log.info("today.isEqual(endDate): {}", today.isEqual(endDate));
        log.info("today.isBefore(endDate): {}", today.isBefore(endDate));
        log.info("===============================");
        return today.isBefore(endDate) || today.isEqual(endDate);
    }
    @Override
    public int getDaysRemaining(UserServiceSubscription subscription) {
        if (subscription == null || !calculateSubscriptionActive(subscription)) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        LocalDate endDate = subscription.getSubscriptionEndDate();
        long daysBetween = ChronoUnit.DAYS.between(today, endDate);
        return Math.max(0, (int) daysBetween);
    }
}
