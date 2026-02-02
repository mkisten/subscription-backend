package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import com.mkisten.vacancybackend.dto.TokenResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyAutoUpdater {

    private final UserSettingsRepository userSettingsRepository;
    private final VacancySmartService vacancySmartService;
    private final AuthServiceClient authServiceClient;

    private static final int BATCH_SIZE = 200;
    private static final int JITTER_PERCENT = 20;

    @Scheduled(fixedRate = 60000)
    public void updateAllUsers() {
        log.info("== Автообновление вакансий (пачка) ==");
        LocalDateTime now = LocalDateTime.now();
        List<UserSettings> settingsList = userSettingsRepository.findDueUsers(
                now,
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Order.asc("nextRunAt")))
        );

        for (UserSettings settings : settingsList) {
            try {
                String token = getTokenForUser(settings);
                if (token == null) {
                    log.warn("Токен для пользователя {} не получен, пропускаем", settings.getTelegramId());
                    continue;
                }

                SubscriptionStatusResponse status = authServiceClient.getSubscriptionStatus(token);
                if (status == null || !Boolean.TRUE.equals(status.getActive())) {
                    log.info("Подписка не активна для пользователя {}, автообновление пропущено",
                            settings.getTelegramId());
                    continue;
                }

                // Подготовка запроса на  основе пользовательских настроек
                SearchRequest request = new SearchRequest();
                request.setQuery(settings.getSearchQuery());
                request.setDays(settings.getDays());
                request.setWorkTypes(settings.getWorkTypes());
                request.setCountries(settings.getCountries());
                request.setExcludeKeywords(settings.getExcludeKeywords());
                request.setTelegramNotify(settings.getTelegramNotify());

                // Поиск, сохранение и отправка
                List<Vacancy> foundVacancies = vacancySmartService.searchWithUserSettings(
                        request, token, settings.getTelegramId());

                log.info("Auto-update completed for user {}. Found {} vacancies",
                        settings.getTelegramId(), foundVacancies.size());

            } catch (Exception e) {
                log.error("Ошибка автообновления для user: {} — {}", settings.getTelegramId(), e.getMessage(), e);
            } finally {
                scheduleNextRun(settings, now);
                userSettingsRepository.save(settings);
            }
        }
        log.info("== Автообновление вакансий завершено. Обработано: {} ==", settingsList.size());
    }

    private String getTokenForUser(UserSettings settings) {
        try {
            Long telegramId = settings.getTelegramId();
            if (telegramId == null) return null;
            TokenResponse resp = authServiceClient.getTokenByTelegramId(telegramId);
            if (resp == null || resp.getToken() == null || resp.getToken().isBlank()) {
                log.warn("AuthServiceClient вернул пустой токен для {}", telegramId);
                return null;
            }
            return resp.getToken();
        } catch (Exception e) {
            log.error("getTokenForUser: ошибка получения токена для {}: {}", settings.getTelegramId(), e.getMessage());
            return null;
        }
    }

    private void scheduleNextRun(UserSettings settings, LocalDateTime baseTime) {
        int interval = settings.getAutoUpdateInterval() == null ? 30 : settings.getAutoUpdateInterval();
        if (interval < 1) {
            interval = 1;
        }
        int jitterMax = Math.max(1, (int) Math.round(interval * (JITTER_PERCENT / 100.0)));
        int jitter = ThreadLocalRandom.current().nextInt(jitterMax + 1);

        settings.setLastRunAt(baseTime);
        settings.setNextRunAt(baseTime.plusMinutes(interval + jitter));
    }
}
