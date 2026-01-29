package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository settingsRepository;
    private final AuthServiceClient authServiceClient;
    private final TelegramNotificationService telegramService;

    /** Получить текущего пользователя из токена */
    private Long getTelegramIdByToken(String token) {

        return authServiceClient.getCurrentUserProfile(token).getTelegramId();
    }

    @Transactional(readOnly = true)
    public UserSettings getSettings(String token) {
        Long telegramId = getTelegramIdByToken(token);
        return settingsRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createDefaultSettings(telegramId));
    }

    /** Проверить подписку (через токен) */
    public boolean isSubscriptionActive(String token) {
        try {
            var status = authServiceClient.getSubscriptionStatus(token);
            return status.getActive();
        } catch (Exception e) {
            log.error("Failed to check subscription status: {}", e.getMessage());
            return false;
        }
    }

    /** Получить информацию о подписке (через токен) */
    public SubscriptionStatusResponse getSubscriptionInfo(String token) {
        return authServiceClient.getSubscriptionStatus(token);
    }

    @Transactional
    public UserSettings updateSettings(String token, UserSettings newSettings) {
        Long telegramId = getTelegramIdByToken(token);
        UserSettings existingSettings = settingsRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createDefaultSettings(telegramId));
        // обновляем поля как раньше...
        existingSettings.setSearchQuery(newSettings.getSearchQuery());
        // ...и другие поля

        UserSettings saved = settingsRepository.save(existingSettings);

        // Отправить уведомление об обновлении
        if (Boolean.TRUE.equals(saved.getTelegramNotify())) {
            try {
                telegramService.sendSettingsUpdatedNotification(token);
            } catch (Exception e) {
                log.warn("Failed to send settings update notification: {}", e);
            }
        }
        log.info("Settings updated for user {}", telegramId);
        return saved;
    }

    @Transactional
    public void setupAutoUpdate(String token, Boolean enabled, Integer intervalMinutes) {
        Long telegramId = getTelegramIdByToken(token);
        UserSettings settings = getSettings(token);
        settings.setAutoUpdateEnabled(enabled);
        settings.setAutoUpdateInterval(intervalMinutes);
        settingsRepository.save(settings);
        log.info("Auto-update settings updated for user {}: enabled={}, interval={}min",
                telegramId, enabled, intervalMinutes);
    }

    private UserSettings createDefaultSettings(Long telegramId) {
        UserSettings settings = new UserSettings(telegramId);
        return settingsRepository.save(settings);
    }
}


