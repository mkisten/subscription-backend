package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionStatusService {

    private final UserService userService;
    private final TelegramAuthService telegramAuthService;

    /**
     * Ежедневное обновление статусов подписок по всем сервисам
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void updateAllSubscriptionStatuses() {
        try {
            log.info("Starting bulk subscription status update...");

            List<UserServiceSubscription> allSubscriptions = userService.getAllUserServices();
            int updatedCount = 0;

            for (UserServiceSubscription subscription : allSubscriptions) {
                boolean currentStatus = subscription.isActive();
                boolean calculatedStatus = telegramAuthService.calculateSubscriptionActive(subscription);

                if (currentStatus != calculatedStatus) {
                    subscription.setActive(calculatedStatus);
                    updatedCount++;

                    log.info("Updated subscription status for user {} service {}: {} -> {}",
                            subscription.getUser().getTelegramId(), subscription.getServiceCode(),
                            currentStatus, calculatedStatus);
                }
            }

            log.info("Bulk subscription status update completed. Updated {} subscriptions", updatedCount);

        } catch (Exception e) {
            log.error("Error updating subscription statuses", e);
        }
    }

    /**
     * Принудительное обновление статуса для конкретной подписки
     */
    @Transactional
    public void updateUserSubscriptionStatus(UserServiceSubscription subscription) {
        if (subscription == null) {
            return;
        }
        try {
            boolean currentStatus = subscription.isActive();
            boolean newStatus = telegramAuthService.calculateSubscriptionActive(subscription);

            if (currentStatus != newStatus) {
                subscription.setActive(newStatus);
                log.info("Manually updated subscription status for user {} service {}: {} -> {}",
                        subscription.getUser().getTelegramId(), subscription.getServiceCode(), currentStatus, newStatus);
            } else {
                log.debug("Subscription status for user {} service {} is already correct: {}",
                        subscription.getUser().getTelegramId(), subscription.getServiceCode(), currentStatus);
            }
        } catch (Exception e) {
            log.error("Error updating subscription status for user {} service {}",
                    subscription.getUser().getTelegramId(), subscription.getServiceCode(), e);
        }
    }

    /**
     * Обновляет статус подписки для пользователя (синхронная версия)
     */
    @Transactional
    public void refreshUserSubscriptionStatus(UserServiceSubscription subscription) {
        if (subscription == null) return;

        boolean newStatus = telegramAuthService.calculateSubscriptionActive(subscription);
        if (subscription.isActive() != newStatus) {
            subscription.setActive(newStatus);
            log.debug("Refreshed subscription status for user {} service {}: {}",
                    subscription.getUser().getTelegramId(), subscription.getServiceCode(), newStatus);
        }
    }
}
