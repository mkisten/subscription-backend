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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyAutoUpdater {

    private final UserSettingsRepository userSettingsRepository;
    private final VacancySmartService vacancySmartService;
    private final AuthServiceClient authServiceClient;

    private static final int BATCH_SIZE = 200;
    private static final int JITTER_PERCENT = 20;

    @Value("${app.auto-update.workers:1}")
    private int workerCount;

    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();
    private final Set<Long> queuedUsers = ConcurrentHashMap.newKeySet();
    private ExecutorService workerPool;
    private volatile boolean running = true;

    @PostConstruct
    public void startWorkers() {
        int threads = Math.max(1, workerCount);
        AtomicInteger index = new AtomicInteger(1);
        workerPool = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task);
            thread.setName("vacancy-auto-update-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        for (int i = 0; i < threads; i++) {
            workerPool.submit(this::workerLoop);
        }
        log.info("Auto-update workers started: {}", threads);
    }

    @PreDestroy
    public void stopWorkers() {
        running = false;
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
    }

    @Scheduled(fixedRate = 60000)
    public void updateAllUsers() {
        log.info("== Автообновление вакансий (пачка) ==");
        LocalDateTime now = LocalDateTime.now();
        List<UserSettings> settingsList = userSettingsRepository.findDueUsers(
                now,
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Order.asc("nextRunAt")))
        );

        int enqueued = 0;
        for (UserSettings settings : settingsList) {
            if (settings.getId() == null) {
                continue;
            }
            if (queuedUsers.add(settings.getId())) {
                queue.offer(settings.getId());
                enqueued++;
            }
        }

        log.info("== Автообновление вакансий завершено. Обработано: {}, в очереди: {} ==",
                settingsList.size(), enqueued);
    }

    private void workerLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Long settingsId = queue.take();
                processUser(settingsId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processUser(Long settingsId) {
        try {
            Optional<UserSettings> optionalSettings = userSettingsRepository.findById(settingsId);
            if (optionalSettings.isEmpty()) {
                return;
            }
            UserSettings settings = optionalSettings.get();
            if (settings.getAutoUpdateEnabled() == null || !settings.getAutoUpdateEnabled()) {
                return;
            }

            String token = getTokenForUser(settings);
            if (token == null) {
                log.warn("Токен для пользователя {} не получен, пропускаем", settings.getTelegramId());
                return;
            }

            SubscriptionStatusResponse status = authServiceClient.getSubscriptionStatus(token);
            if (status == null || !Boolean.TRUE.equals(status.getActive())) {
                log.info("Подписка не активна для пользователя {}, автообновление пропущено",
                        settings.getTelegramId());
                return;
            }

            // Подготовка запроса на основе пользовательских настроек
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
            log.error("Ошибка автообновления для user: {} — {}", settingsId, e.getMessage(), e);
        } finally {
            try {
                LocalDateTime now = LocalDateTime.now();
                userSettingsRepository.findById(settingsId).ifPresent(settings -> {
                    scheduleNextRun(settings, now);
                    userSettingsRepository.save(settings);
                });
            } finally {
                queuedUsers.remove(settingsId);
            }
        }
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
