package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

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

    /** Получить Telegram ID из токена без обращения к настройкам */
    public Long getTelegramId(String token) {
        return getTelegramIdByToken(token);
    }

    @Transactional(readOnly = true)
    public UserSettings getSettings(String token) {
        Long telegramId = getTelegramIdByToken(token);
        return settingsRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createDefaultSettings(telegramId));
    }

    @Transactional(readOnly = true)
    public UserSettings getSettingsByTelegramId(Long telegramId) {
        return settingsRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Settings not found for telegramId " + telegramId));
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
        if (newSettings.getSearchQuery() != null) {
            existingSettings.setSearchQuery(newSettings.getSearchQuery());
        }
        if (newSettings.getDays() != null) {
            existingSettings.setDays(newSettings.getDays());
        }
        if (newSettings.getExcludeKeywords() != null) {
            existingSettings.setExcludeKeywords(newSettings.getExcludeKeywords());
        }
        if (newSettings.getWorkTypes() != null) {
            existingSettings.setWorkTypes(newSettings.getWorkTypes());
        }
        if (newSettings.getCountries() != null) {
            existingSettings.setCountries(newSettings.getCountries());
        }
        if (newSettings.getTelegramNotify() != null) {
            existingSettings.setTelegramNotify(newSettings.getTelegramNotify());
        }
        if (newSettings.getAutoUpdateEnabled() != null) {
            existingSettings.setAutoUpdateEnabled(newSettings.getAutoUpdateEnabled());
        }
        if (newSettings.getAutoUpdateInterval() != null) {
            existingSettings.setAutoUpdateInterval(newSettings.getAutoUpdateInterval());
        }
        if (newSettings.getTheme() != null) {
            existingSettings.setTheme(newSettings.getTheme());
        }

        applyAutoUpdateSchedule(existingSettings);
        UserSettings saved = settingsRepository.save(existingSettings);

        // Отправить уведомление об обновлении
        if (Boolean.TRUE.equals(saved.getTelegramNotify())) {
            try {
                telegramService.sendSettingsUpdatedNotification(token);
            } catch (Exception e) {
                log.warn("Failed to send settings update notification: {}", e);
            }
        }
        log.info(
                "Настройки пользователя {} обновлены: ключевые слова='{}', исключения='{}', период={} дн., типы работы={}, регионы={}, автообновление={}, интервал автообновления={} мин., рассылка в Telegram={}, тема='{}'",
                telegramId,
                saved.getSearchQuery(),
                saved.getExcludeKeywords(),
                saved.getDays(),
                saved.getWorkTypes(),
                saved.getCountries(),
                saved.getAutoUpdateEnabled(),
                saved.getAutoUpdateInterval(),
                saved.getTelegramNotify(),
                saved.getTheme()
        );
        return saved;
    }

    @Transactional
    public void setupAutoUpdate(String token, Boolean enabled, Integer intervalMinutes) {
        Long telegramId = getTelegramIdByToken(token);
        UserSettings settings = getSettings(token);
        settings.setAutoUpdateEnabled(enabled);
        settings.setAutoUpdateInterval(intervalMinutes);
        applyAutoUpdateSchedule(settings);
        settingsRepository.save(settings);
        log.info("Auto-update settings updated for user {}: enabled={}, interval={}min",
                telegramId, enabled, intervalMinutes);
    }

    private UserSettings createDefaultSettings(Long telegramId) {
        UserSettings settings = new UserSettings(telegramId);
        return settingsRepository.save(settings);
    }

    private void applyAutoUpdateSchedule(UserSettings settings) {
        if (!Boolean.TRUE.equals(settings.getAutoUpdateEnabled())) {
            settings.setNextRunAt(null);
            return;
        }

        int interval = settings.getAutoUpdateInterval() == null ? 30 : settings.getAutoUpdateInterval();
        if (interval < 1) {
            interval = 1;
        }
        int jitterMax = Math.max(1, (int) Math.round(interval * 0.2));
        int jitter = ThreadLocalRandom.current().nextInt(jitterMax + 1);
        settings.setNextRunAt(LocalDateTime.now().plusMinutes(interval + jitter));
    }
}


