package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class SubscriptionCalculatorImpl implements SubscriptionCalculator {
    @Override
    public boolean calculateSubscriptionActive(User user) {
        if (user == null || user.getSubscriptionEndDate() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate endDate = user.getSubscriptionEndDate();
        log.info("=== Расчет статуса подписки ===");
        log.info("Telegram ID: {}", user.getTelegramId());
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
    public int getDaysRemaining(User user) {
        if (user == null || !calculateSubscriptionActive(user)) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        LocalDate endDate = user.getSubscriptionEndDate();
        long daysBetween = ChronoUnit.DAYS.between(today, endDate);
        return Math.max(0, (int) daysBetween);
    }
}
