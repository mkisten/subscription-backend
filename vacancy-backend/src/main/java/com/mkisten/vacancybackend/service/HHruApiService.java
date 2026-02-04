package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
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

    @Value("${app.hhru.max-pages:20}")
    private int maxPages;

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

            int days = request.getDays() != null ? request.getDays() : 1;
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

            List<Vacancy> allVacancies = new ArrayList<>();
            int currentPage = 0;
            Integer totalPages = null;

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; VacancyBot/1.0)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            while (true) {
                if (totalPages != null && currentPage >= totalPages) {
                    break;
                }
                if (currentPage >= maxPages) {
                    log.info("HH.ru pagination stopped at maxPages={}", maxPages);
                    break;
                }

                java.net.URI uri = buildSearchUri(request, currentPage);
                log.info("HH.ru search URL: {}", uri);

                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
                Map<String, Object> body = response.getBody();
                if (body == null) {
                    log.warn("HH.ru response body is null for page {}", currentPage);
                    break;
                }

                Object foundRaw = body.get("found");
                Integer found = null;
                if (foundRaw instanceof Number) {
                    found = ((Number) foundRaw).intValue();
                }

                Object pagesRaw = body.get("pages");
                if (pagesRaw instanceof Number) {
                    totalPages = ((Number) pagesRaw).intValue();
                }

                List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
                int size = items == null ? -1 : items.size();
                log.info("HH.ru response: status={}, found={}, items={}, page={}, pages={}",
                        response.getStatusCode(),
                        found,
                        size,
                        currentPage,
                        totalPages);

                if (items == null) {
                    log.warn("HH.ru response has no items. Keys={}", body.keySet());
                    break;
                }
                if (items.isEmpty()) {
                    if (found != null && found > 0) {
                        log.warn("HH.ru returned found={} but empty items array", found);
                    }
                    break;
                }

                List<Vacancy> pageVacancies = convertToVacancies(items, telegramId, cutoff);
                allVacancies.addAll(pageVacancies);

                boolean hasFresh = hasVacanciesNewerThan(items, cutoff);
                if (!hasFresh) {
                    log.info("HH.ru pagination stopped: page {} is older than cutoff {}", currentPage, cutoff);
                    break;
                }

                currentPage += 1;
            }

            return allVacancies;
        } catch (Exception e) {
            log.error("Error searching vacancies on HH.ru: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private java.net.URI buildSearchUri(SearchRequest request, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/vacancies")
                .queryParam("text", request.getQuery())
                .queryParam("period", request.getDays())
                .queryParam("per_page", 100)
                .queryParam("page", page)
                .queryParam("only_with_salary", false)
                .queryParam("search_field", "name");

        // Добавляем фильтры по area (регион)
        if (request.getCountries() != null && !request.getCountries().isEmpty()) {
            if (request.getCountries().contains("russia")) {
                builder.queryParam("area", 113); // Россия
            }
            if (request.getCountries().contains("belarus")) {
                builder.queryParam("area", 16); // Беларусь
            }
        }

        return builder.build().encode(java.nio.charset.StandardCharsets.UTF_8).toUri();
    }

    private List<Vacancy> convertToVacancies(List<Map<String, Object>> items, Long telegramId, LocalDateTime cutoff) {
        List<Vacancy> vacancies = new ArrayList<>();
        if (items == null) return vacancies;

        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;

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

                // Work format (REMOTE / HYBRID / ON_SITE) -> нормализуем в человекочитаемый вид
                String workFormatLabel = extractWorkFormatLabel(item);
                if (workFormatLabel != null) {
                    vacancy.setSchedule(workFormatLabel);
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
                        if (cutoff != null && publishedDateTime.isBefore(cutoff)) {
                            skippedCount++;
                            continue;
                        }
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
        if (skippedCount > 0) {
            log.info("Skipped {} vacancies older than cutoff", skippedCount);
        }
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

    private boolean hasVacanciesNewerThan(List<Map<String, Object>> items, LocalDateTime cutoff) {
        if (cutoff == null) {
            return true;
        }
        for (Map<String, Object> item : items) {
            String publishedAt = (String) item.get("published_at");
            if (publishedAt == null) {
                continue;
            }
            try {
                LocalDateTime publishedDateTime = LocalDateTime.parse(publishedAt, hhruDateFormatter);
                if (!publishedDateTime.isBefore(cutoff)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Failed to parse date for freshness check: {}", e.getMessage());
            }
        }
        return false;
    }

    private String extractWorkFormatLabel(Map<String, Object> item) {
        List<String> labels = new ArrayList<>();
        Object workFormatObj = item.get("work_format");
        if (workFormatObj instanceof List) {
            List<Map<String, Object>> workFormats = (List<Map<String, Object>>) workFormatObj;
            for (Map<String, Object> wf : workFormats) {
                String id = wf.get("id") != null ? wf.get("id").toString() : "";
                switch (id) {
                    case "REMOTE":
                        labels.add("Удалённо");
                        break;
                    case "HYBRID":
                        labels.add("Гибрид");
                        break;
                    case "ON_SITE":
                        labels.add("Офис");
                        break;
                    default:
                        String name = wf.get("name") != null ? wf.get("name").toString() : null;
                        if (name != null && !name.isBlank()) {
                            labels.add(name);
                        }
                        break;
                }
            }
        }

        if (!labels.isEmpty()) {
            return String.join(", ", labels);
        }

        // Фоллбек на schedule, если work_format отсутствует
        Map<String, Object> schedule = (Map<String, Object>) item.get("schedule");
        if (schedule != null) {
            String scheduleName = schedule.get("name") != null ? schedule.get("name").toString() : "";
            if (scheduleName.toLowerCase().contains("удал")) {
                return "Удалённо";
            }
            return "Офис";
        }

        return null;
    }
}
