package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HHruApiService {

    private final RestTemplate restTemplate;
    private final AuthServiceClient authServiceClient;

    @Value("${app.hhru.base-url}")
    private String baseUrl;

    @Value("${app.hhru.timeout:10000}")
    private int timeout;

    // Форматтер для дат HH.ru (поддерживает разные форматы)
    private final DateTimeFormatter hhruDateFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendOffset("+HHMM", "+0000") // для +0300, +0400 и т.д.
            .optionalEnd()
            .optionalStart()
            .appendPattern("XXX") // для +03:00
            .optionalEnd()
            .parseDefaulting(ChronoField.OFFSET_SECONDS, 0) // по умолчанию UTC
            .toFormatter();

    /**
     * Новый метод: теперь всегда нужен token пользователя.
     */
    public List<Vacancy> searchVacancies(SearchRequest request, String token) {
        try {

            // Получаем профиль пользователя через AuthServiceClient
            ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
            Long telegramId = profile.getTelegramId();

            String url = buildSearchUrl(request);
            log.debug("Searching vacancies with URL: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getBody() != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
                return convertToVacancies(items, telegramId);
            }
        } catch (Exception e) {
            log.error("Error searching vacancies on HH.ru: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private String buildSearchUrl(SearchRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/vacancies")
                .queryParam("text", request.getQuery())
                .queryParam("period", request.getDays())
                .queryParam("per_page", 100)
                .queryParam("page", 0)
                .queryParam("only_with_salary", false)
                .queryParam("search_field", "name");

        // Добавляем фильтры по schedule (тип работы)
        if (request.getWorkTypes() != null && !request.getWorkTypes().isEmpty()) {
            request.getWorkTypes().forEach(workType -> {
                switch (workType) {
                    case "remote":
                        builder.queryParam("schedule", "remote");
                        break;
                    case "hybrid":
                        // HH.ru не имеет прямого параметра для гибрида
                        break;
                    case "office":
                        builder.queryParam("schedule", "fullDay");
                        break;
                }
            });
        }

        // Добавляем фильтры по area (регион)
        if (request.getCountries() != null && !request.getCountries().isEmpty()) {
            if (request.getCountries().contains("russia")) {
                builder.queryParam("area", 113); // Россия
            }
            if (request.getCountries().contains("belarus")) {
                builder.queryParam("area", 16); // Беларусь
            }
        }

        return builder.toUriString();
    }

    private List<Vacancy> convertToVacancies(List<Map<String, Object>> items, Long telegramId) {
        List<Vacancy> vacancies = new ArrayList<>();
        if (items == null) return vacancies;

        int successCount = 0;
        int errorCount = 0;

        for (Map<String, Object> item : items) {
            try {
                Vacancy vacancy = new Vacancy();
                vacancy.setId(item.get("id").toString());
                vacancy.setUserTelegramId(telegramId);
                vacancy.setTitle((String) item.get("name"));

                // Employer
                Map<String, Object> employer = (Map<String, Object>) item.get("employer");
                if (employer != null) {
                    vacancy.setEmployer((String) employer.get("name"));
                }

                // Area (city)
                Map<String, Object> area = (Map<String, Object>) item.get("area");
                if (area != null) {
                    vacancy.setCity((String) area.get("name"));
                }

                // Schedule
                Map<String, Object> schedule = (Map<String, Object>) item.get("schedule");
                if (schedule != null) {
                    vacancy.setSchedule((String) schedule.get("name"));
                }

                // Salary
                Map<String, Object> salary = (Map<String, Object>) item.get("salary");
                if (salary != null) {
                    vacancy.setSalary(formatSalary(salary));
                }

                // Published date - исправленный парсинг
                String publishedAt = (String) item.get("published_at");
                if (publishedAt != null) {
                    try {
                        LocalDateTime publishedDateTime = LocalDateTime.parse(publishedAt, hhruDateFormatter);
                        vacancy.setPublishedAt(publishedDateTime);
                    } catch (Exception e) {
                        log.warn("Failed to parse date '{}': {}", publishedAt, e.getMessage());
                        vacancy.setPublishedAt(LocalDateTime.now());
                    }
                } else {
                    vacancy.setPublishedAt(LocalDateTime.now());
                }

                // URL
                vacancy.setUrl((String) item.get("alternate_url"));

                vacancies.add(vacancy);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.warn("Error converting vacancy item '{}': {}", item.get("name"), e.getMessage());
            }
        }

        log.info("Successfully converted {}/{} vacancies from HH.ru response", successCount, items.size());
        if (errorCount > 0) {
            log.warn("Failed to convert {} vacancies due to errors", errorCount);
        }

        return vacancies;
    }

    private String formatSalary(Map<String, Object> salary) {
        try {
            String from = salary.get("from") != null ? salary.get("from").toString() : "";
            String to = salary.get("to") != null ? salary.get("to").toString() : "";
            String currency = (String) salary.get("currency");

            if (!from.isEmpty() && !to.isEmpty()) {
                return from + " - " + to + " " + currency;
            } else if (!from.isEmpty()) {
                return "от " + from + " " + currency;
            } else if (!to.isEmpty()) {
                return "до " + to + " " + currency;
            }
        } catch (Exception e) {
            log.debug("Error formatting salary: {}", e.getMessage());
        }
        return "Не указана";
    }
}
