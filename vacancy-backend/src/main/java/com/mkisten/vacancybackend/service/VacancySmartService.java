package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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

        // Сохраняем только новые вакансии (проверяется уникальность по (id+userTelegramId))
        vacancyService.saveVacancies(token, foundVacancies);

        // Отправить только неотправленные вакансии в Telegram
        if (Boolean.TRUE.equals(settings.getTelegramNotify())) {
            telegramService.sendAllUnsentVacanciesToTelegram(token, userTelegramId);
        }

        // Возвращаем все найденные вакансии
        return foundVacancies;
    }
}
