package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        log.info("Smart search for user {} with query: {}", userTelegramId, request.getQuery());

        // Поиск вакансий через hhruApiService
        List<Vacancy> foundVacancies = hhruApiService.searchVacancies(request, token);
        List<Vacancy> filteredVacancies = filterByExcludeKeywords(foundVacancies, request.getExcludeKeywords());

        // Сохраняем только новые вакансии (проверяется уникальность по (id+userTelegramId))
        vacancyService.saveVacancies(token, filteredVacancies);

        // Отправить только неотправленные вакансии в Telegram
        if (Boolean.TRUE.equals(settings.getTelegramNotify())) {
            telegramService.sendAllUnsentVacanciesToTelegram(token, userTelegramId);
        }

        // Возвращаем все найденные вакансии
        return filteredVacancies;
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
