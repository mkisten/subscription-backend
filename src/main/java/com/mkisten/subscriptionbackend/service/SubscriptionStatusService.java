package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionStatusService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TelegramAuthService telegramAuthService;

    /**
     * Ежедневное обновление статусов подписок
     */
    @Scheduled(cron = "0 0 0 * * ?") // Каждый день в полночь
    @Transactional
    public void updateAllSubscriptionStatuses() {
        try {
            log.info("Starting bulk subscription status update...");

            List<User> allUsers = userService.getAllUsers();
            int updatedCount = 0;

            for (User user : allUsers) {
                boolean currentStatus = user.isActive();
                boolean calculatedStatus = telegramAuthService.calculateSubscriptionActive(user);

                if (currentStatus != calculatedStatus) {
                    user.setActive(calculatedStatus);
                    userRepository.save(user);
                    updatedCount++;

                    log.info("Updated subscription status for user {}: {} -> {}",
                            user.getTelegramId(), currentStatus, calculatedStatus);
                }
            }

            log.info("Bulk subscription status update completed. Updated {} users", updatedCount);

        } catch (Exception e) {
            log.error("Error updating subscription statuses", e);
        }
    }

    /**
     * Принудительное обновление статуса для конкретного пользователя
     */
    @Transactional
    public void updateUserSubscriptionStatus(Long telegramId) {
        try {
            User user = userService.findByTelegramId(telegramId);
            boolean currentStatus = user.isActive();
            boolean newStatus = telegramAuthService.calculateSubscriptionActive(user);

            if (currentStatus != newStatus) {
                user.setActive(newStatus);
                userRepository.save(user);
                log.info("Manually updated subscription status for user {}: {} -> {}",
                        telegramId, currentStatus, newStatus);
            } else {
                log.debug("Subscription status for user {} is already correct: {}", telegramId, currentStatus);
            }
        } catch (Exception e) {
            log.error("Error updating subscription status for user {}", telegramId, e);
        }
    }

    /**
     * Обновляет статус подписки для пользователя (синхронная версия)
     */
    @Transactional
    public void refreshUserSubscriptionStatus(User user) {
        if (user == null) return;

        boolean newStatus = telegramAuthService.calculateSubscriptionActive(user);
        if (user.isActive() != newStatus) {
            user.setActive(newStatus);
            userRepository.save(user);
            log.debug("Refreshed subscription status for user {}: {}", user.getTelegramId(), newStatus);
        }
    }
}