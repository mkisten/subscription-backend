package com.mkisten.vacancybackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramNotificationService {

    private final AuthServiceClient authServiceClient;
    private final VacancyRepository vacancyRepository;

    @Value("${app.telegram.max-vacancies-per-message:10}")
    private int maxVacanciesPerMessage;

    public TelegramNotificationService(AuthServiceClient authServiceClient, VacancyRepository vacancyRepository) {
        this.authServiceClient = authServiceClient;
        this.vacancyRepository = vacancyRepository;
    }

    private final Cache<Long, List<Vacancy>> vacanciesCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    public void sendAllUnsentVacanciesToTelegram(String userToken, Long userTelegramId) {
        List<Vacancy> unsent = vacanciesCache.get(userTelegramId, id ->
                new ArrayList<>(vacancyRepository.findByUserTelegramIdAndSentToTelegramFalseOrderByPublishedAtAsc(id)));

        if (unsent == null || unsent.isEmpty()) {
            log.info("Нет новых вакансий для отправки в Telegram для пользователя {}", userTelegramId);
            vacanciesCache.invalidate(userTelegramId);
            return;
        }

        log.info("Всего неотправленных вакансий для пользователя {}: {}", userTelegramId, unsent.size());

        List<String> sentIds = new ArrayList<>();
        int batchNumber = 0;

        while (!unsent.isEmpty()) {
            batchNumber++;
            List<Vacancy> batch = unsent.stream().limit(maxVacanciesPerMessage).collect(Collectors.toList());
            String message = formatNewVacanciesMessage(batch);

            try {
                sendTextMessage(userToken, message);
                log.info("Batch #{}: отправлено {} вакансий для user {}", batchNumber, batch.size(), userTelegramId);
                sentIds.addAll(batch.stream().map(Vacancy::getId).toList());
                unsent.removeAll(batch);
            } catch (Exception e) {
                log.error("Ошибка отправки Telegram batch #{}: {}", batchNumber, e.getMessage());
                break;
            }
        }

        if (!sentIds.isEmpty()) {
            int updated = vacancyRepository.markAsSentToTelegram(userTelegramId, sentIds);
            log.info("Помечено отправленными в БД {} вакансий для user {}", updated, userTelegramId);
            if (updated != sentIds.size()) {
                log.warn("После отправки батчей user {}: ожидали отметить {} вакансий, но обновили только {}. Возможны повторы.", userTelegramId, sentIds.size(), updated);
            }
            int remainingUnsent = vacancyRepository.countByUserTelegramIdAndSentToTelegramFalse(userTelegramId);
            if (remainingUnsent > 0) {
                log.warn("После отправки user {} в очереди осталось {} неотправленных вакансий. Возможны повторы.", userTelegramId, remainingUnsent);
            }
        }

        vacanciesCache.invalidate(userTelegramId);
    }

    public void sendTextMessage(String userToken, String text) {
        try {
            authServiceClient.sendTelegramNotification(userToken, text);
            log.debug("Сообщение отправлено через AuthService");
        } catch (Exception e) {
            log.error("Не удалось отправить сообщение: {}", e.getMessage());
            throw new RuntimeException("Ошибка отправки в Telegram", e);
        }
    }

    public void sendTestNotification(String userToken) {
        String message = "🧪 <b>Тестовое уведомление</b>\n\n" +
                "Это тестовое сообщение от сервиса вакансий.\n" +
                "Если вы получили это сообщение, значит уведомления работают корректно! ✅";
        sendTextMessage(userToken, message);
        log.info("Test notification sent via AuthService");
    }

    public void sendErrorNotification(String userToken, String errorMessage) {
        String message = "❌ <b>Произошла ошибка</b>\n\n" +
                "При обработке вашего запроса возникла ошибка:\n" +
                "<code>" + escapeHtml(errorMessage) + "</code>\n\n" +
                "Пожалуйста, попробуйте позже или обратитесь в поддержку.";
        sendTextMessage(userToken, message);
    }

    public void sendSettingsUpdatedNotification(String userToken) {
        String message = "✅ <b>Настройки обновлены</b>\n\n" +
                "Ваши настройки поиска вакансий были успешно сохранены.\n" +
                "Автообновление будет работать в фоновом режиме.";
        sendTextMessage(userToken, message);
    }

    public void sendStatisticsNotification(String userToken, long totalVacancies, long newVacancies) {
        String message = "📊 <b>Статистика вакансий</b>\n\n" +
                "Всего вакансий: <b>" + totalVacancies + "</b>\n" +
                "Новых вакансий: <b>" + newVacancies + "</b>\n\n" +
                "Используйте приложение для просмотра деталей.";
        sendTextMessage(userToken, message);
    }

    private String formatNewVacanciesMessage(List<Vacancy> vacancies) {
        StringBuilder sb = new StringBuilder();
        if (vacancies.size() == 1) {
            sb.append("🎯 Найдена новая вакансия:\n\n");
        } else {
            sb.append("🎯 Новые вакансии (").append(vacancies.size()).append("):\n\n");
        }
        int i = 0;
        for (Vacancy vacancy : vacancies) {
            sb.append(formatSingleVacancy(vacancy));
            if (++i < vacancies.size()) {
                sb.append("\n").append("─".repeat(30)).append("\n\n");
            }
        }
        sb.append("\n\n🚀 Открывайте приложение для просмотра всех вакансий!");
        return sb.toString();
    }

    private String formatSingleVacancy(Vacancy vacancy) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 *").append(escapeMarkdown(vacancy.getTitle())).append("*\n");
        sb.append("🗓 *Публикация:* ").append(formatDate(vacancy.getPublishedAt())).append("\n");
        sb.append("🏢 *Компания:* ").append(escapeMarkdown(vacancy.getEmployer() != null ? vacancy.getEmployer() : "Не указана")).append("\n");
        sb.append("📍 *Город:* ").append(escapeMarkdown(vacancy.getCity() != null ? vacancy.getCity() : "Не указан")).append("\n");
        String schedule = vacancy.getSchedule() != null ? formatSchedule(vacancy.getSchedule()) : "Не указан";
        sb.append("📊 *Формат:* ").append(escapeMarkdown(schedule)).append("\n");
        String salary = vacancy.getSalary() != null && !vacancy.getSalary().equals("Не указана") ? vacancy.getSalary() : "не указана";
        sb.append("💰 *Зарплата:* ").append(escapeMarkdown(salary)).append("\n");
        sb.append("🔗 *Ссылка:* ").append(vacancy.getUrl());
        return sb.toString();
    }

    private String formatDate(java.time.LocalDateTime publishedAt) {
        if (publishedAt == null) return "не указана";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return publishedAt.format(fmt);
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    private String formatSchedule(String schedule) {
        switch (schedule.toLowerCase()) {
            case "remote": return "🏠 Удаленная работа";
            case "fullday": return "📅 Полный день";
            case "shift": return "🔄 Сменный график";
            case "flexible": return "⏰ Гибкий график";
            default: return schedule;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}