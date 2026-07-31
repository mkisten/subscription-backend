package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.UserSettingsAudit;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import com.mkisten.vacancybackend.repository.UserSettingsAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class UserSettingsService {

    private final UserSettingsRepository settingsRepository;
    private final AuthServiceClient authServiceClient;
    private final TelegramNotificationService telegramService;
    private final VacancyRepository vacancyRepository;
    private final UserSettingsAuditRepository auditRepository;

    @Autowired
    public UserSettingsService(UserSettingsRepository settingsRepository, AuthServiceClient authServiceClient,
                               TelegramNotificationService telegramService, VacancyRepository vacancyRepository,
                               UserSettingsAuditRepository auditRepository) {
        this.settingsRepository = settingsRepository; this.authServiceClient = authServiceClient;
        this.telegramService = telegramService; this.vacancyRepository = vacancyRepository; this.auditRepository = auditRepository;
    }

    public UserSettingsService(UserSettingsRepository settingsRepository, AuthServiceClient authServiceClient,
                               TelegramNotificationService telegramService, VacancyRepository vacancyRepository) {
        this(settingsRepository, authServiceClient, telegramService, vacancyRepository, null);
    }

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
        UserSettings before = copySettings(existingSettings);
        if (newSettings.getSearchQuery() != null) {
            existingSettings.setSearchQuery(newSettings.getSearchQuery());
        }
        if (newSettings.getDays() != null) {
            existingSettings.setDays(newSettings.getDays());
        }
        if (newSettings.getExcludeKeywords() != null) {
            existingSettings.setExcludeKeywords(newSettings.getExcludeKeywords());
        }
        if (newSettings.getExcludeCompanies() != null) {
            existingSettings.setExcludeCompanies(newSettings.getExcludeCompanies());
        }
        if (newSettings.getCityId() != null) {
            existingSettings.setCityId(newSettings.getCityId());
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
        auditChanges(telegramId, before, saved);
        removeExcludedCompaniesVacancies(saved);

        // Отправить уведомление об обновлении
        if (Boolean.TRUE.equals(saved.getTelegramNotify())) {
            try {
                telegramService.sendSettingsUpdatedNotification(token);
            } catch (Exception e) {
                log.warn("Failed to send settings update notification: {}", e);
            }
        }
        log.info(
                "Настройки пользователя {} обновлены: ключевые слова='{}', исключения='{}', исключённые компании='{}', период={} дн., типы работы={}, регионы={}, автообновление={}, интервал автообновления={} мин., рассылка в Telegram={}, тема='{}'",
                telegramId,
                saved.getSearchQuery(),
                saved.getExcludeKeywords(),
                saved.getExcludeCompanies(),
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
    public UserSettings updateTheme(String token, String theme) {
        if (!"light".equals(theme) && !"dark".equals(theme)) {
            throw new IllegalArgumentException("Theme must be light or dark");
        }
        Long telegramId = getTelegramIdByToken(token);
        UserSettings settings = settingsRepository.findByTelegramId(telegramId).orElseGet(() -> createDefaultSettings(telegramId));
        String oldTheme = settings.getTheme();
        if (!theme.equals(oldTheme)) {
            settings.setTheme(theme);
            UserSettings saved = settingsRepository.save(settings);
            if (auditRepository != null) auditRepository.save(createAudit(telegramId, telegramId, "theme", oldTheme, theme));
            return saved;
        }
        return settings;
    }

    private UserSettings copySettings(UserSettings source) {
        UserSettings copy = new UserSettings();
        copy.setTelegramId(source.getTelegramId()); copy.setTelegramNotify(source.getTelegramNotify());
        copy.setAutoUpdateEnabled(source.getAutoUpdateEnabled()); copy.setAutoUpdateInterval(source.getAutoUpdateInterval());
        copy.setTheme(source.getTheme()); copy.setSearchQuery(source.getSearchQuery());
        return copy;
    }

    private void auditChanges(Long actorId, UserSettings before, UserSettings after) {
        auditIfChanged(actorId, after.getTelegramId(), "telegram_notify", before.getTelegramNotify(), after.getTelegramNotify());
        auditIfChanged(actorId, after.getTelegramId(), "auto_update_enabled", before.getAutoUpdateEnabled(), after.getAutoUpdateEnabled());
        auditIfChanged(actorId, after.getTelegramId(), "auto_update_interval", before.getAutoUpdateInterval(), after.getAutoUpdateInterval());
        auditIfChanged(actorId, after.getTelegramId(), "theme", before.getTheme(), after.getTheme());
        auditIfChanged(actorId, after.getTelegramId(), "search_query", before.getSearchQuery(), after.getSearchQuery());
    }

    private void auditIfChanged(Long actorId, Long targetId, String field, Object oldValue, Object newValue) {
        if (auditRepository != null && !java.util.Objects.equals(oldValue, newValue)) {
            auditRepository.save(createAudit(actorId, targetId, field, oldValue == null ? null : oldValue.toString(), newValue == null ? null : newValue.toString()));
        }
    }

    private UserSettingsAudit createAudit(Long actorId, Long targetId, String field, String oldValue, String newValue) {
        UserSettingsAudit audit = new UserSettingsAudit(); audit.setActorTelegramId(actorId); audit.setTargetTelegramId(targetId);
        audit.setFieldName(field); audit.setOldValue(oldValue); audit.setNewValue(newValue); return audit;
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

    private void removeExcludedCompaniesVacancies(UserSettings settings) {
        Set<String> excludedCompanies = parseCsvLowercase(settings.getExcludeCompanies());
        if (excludedCompanies.isEmpty()) {
            return;
        }
        vacancyRepository.findByUserTelegramIdOrderByStatusAscLoadedAtDesc(settings.getTelegramId()).stream()
                .filter(vacancy -> {
                    String employer = vacancy.getEmployer();
                    if (employer == null || employer.isBlank()) {
                        return false;
                    }
                    String normalizedEmployer = employer.trim().toLowerCase(Locale.ROOT);
                    return excludedCompanies.stream().anyMatch(normalizedEmployer::contains);
                })
                .forEach(vacancy -> vacancyRepository.deleteByUserAndId(settings.getTelegramId(), vacancy.getId()));
    }

    private Set<String> parseCsvLowercase(String rawValue) {
        Set<String> values = new LinkedHashSet<>();
        if (rawValue == null || rawValue.isBlank()) {
            return values;
        }
        for (String raw : rawValue.split(",")) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }
}


