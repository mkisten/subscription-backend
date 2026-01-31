package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancySmartService {
    private final UserSettingsService userSettingsService;
    private final HHruApiService hhruApiService;
    private final TelegramNotificationService telegramService;
    private final VacancyService vacancyService;

    /**
     * Выполняет поиск вакансий с подмешиванием user-настроек,
     * сохраняет новые, и отправляет только неотправленные в Telegram.
     */
    public List<Vacancy> searchWithUserSettings(SearchRequest request, String token, Long userTelegramId) {
        UserSettings settings = userSettingsService.getSettings(token);
        // Подмешивание недостающих настроек из UserSettings
        if (!StringUtils.hasText(request.getQuery()))
            request.setQuery(settings.getSearchQuery());
        if (request.getDays() == null)
            request.setDays(settings.getDays());
        if (request.getExcludeKeywords() == null || request.getExcludeKeywords().isEmpty())
            request.setExcludeKeywords(settings.getExcludeKeywords());
        if (request.getWorkTypes() == null || request.getWorkTypes().isEmpty())
            request.setWorkTypes(settings.getWorkTypes());
        if (request.getCountries() == null || request.getCountries().isEmpty())
            request.setCountries(settings.getCountries());
        if (request.getTelegramNotify() == null)
            request.setTelegramNotify(settings.getTelegramNotify());

        List<String> queries = splitQueries(request.getQuery());
        log.info("Smart search for user {} with queries: {}", userTelegramId, queries);

        // Поиск вакансий по каждому ключевому слову через hhruApiService
        Map<String, Vacancy> uniqueVacancies = new LinkedHashMap<>();
        for (String query : queries) {
            SearchRequest perQuery = new SearchRequest();
            perQuery.setQuery(query);
            perQuery.setDays(request.getDays());
            perQuery.setWorkTypes(request.getWorkTypes());
            perQuery.setCountries(request.getCountries());
            perQuery.setExcludeKeywords(request.getExcludeKeywords());
            perQuery.setTelegramNotify(request.getTelegramNotify());

            List<Vacancy> batch = hhruApiService.searchVacancies(perQuery, token);
            for (Vacancy vacancy : batch) {
                uniqueVacancies.putIfAbsent(vacancy.getId(), vacancy);
            }
        }
        List<Vacancy> filteredVacancies = filterByExcludeKeywords(
                new ArrayList<>(uniqueVacancies.values()),
                request.getExcludeKeywords()
        );

        // Сохраняем только новые вакансии (проверяется уникальность по (id+userTelegramId))
        vacancyService.saveVacancies(token, filteredVacancies);

        // Отправить только неотправленные вакансии в Telegram
        if (Boolean.TRUE.equals(settings.getTelegramNotify())) {
            if (userSettingsService.isSubscriptionActive(token)) {
                telegramService.sendAllUnsentVacanciesToTelegram(token, userTelegramId);
            } else {
                log.info("Подписка не активна для пользователя {}, отправка Telegram отключена", userTelegramId);
            }
        }

        // Возвращаем все найденные вакансии
        return filteredVacancies;
    }

    private List<String> splitQueries(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : query.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? List.of() : result;
    }

    private List<Vacancy> filterByExcludeKeywords(List<Vacancy> vacancies, String excludeKeywords) {
        if (vacancies == null || vacancies.isEmpty()) {
            return vacancies;
        }
        if (!StringUtils.hasText(excludeKeywords)) {
            return vacancies;
        }

        List<String> keywords = new ArrayList<>();
        for (String raw : excludeKeywords.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        if (keywords.isEmpty()) {
            return vacancies;
        }

        List<Vacancy> result = new ArrayList<>(vacancies.size());
        for (Vacancy vacancy : vacancies) {
            String title = vacancy.getTitle();
            String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT);
            boolean excluded = false;
            for (String keyword : keywords) {
                if (normalized.contains(keyword)) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                result.add(vacancy);
            }
        }

        return result;
    }
}
