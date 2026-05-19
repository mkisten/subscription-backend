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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetmatchCareerApiService {

    private final RestTemplate restTemplate;
    private final AuthServiceClient authServiceClient;

    @Value("${app.getmatch.base-url}")
    private String baseUrl;

    @Value("${app.getmatch.max-pages:5}")
    private int maxPages;

    @Value("${app.getmatch.enabled:false}")
    private boolean enabled;

    private final java.time.format.DateTimeFormatter sourceDateFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendOffset("+HHMM", "+0000")
            .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
            .toFormatter();

    public List<Vacancy> searchVacancies(SearchRequest request, String token) {
        if (!enabled) {
            return new ArrayList<>();
        }
        try {
            ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
            Long telegramId = profile.getTelegramId();
            int days = request.getDays() != null ? request.getDays() : 30;
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

            List<Vacancy> allVacancies = new ArrayList<>();
            Integer totalPages = null;
            int currentPage = 0;

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; VacancyBot/1.0)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            while (true) {
                if (totalPages != null && currentPage >= totalPages) {
                    break;
                }
                if (currentPage >= maxPages) {
                    log.info("GetMatch pagination stopped at maxPages={}", maxPages);
                    break;
                }

                java.net.URI uri = buildSearchUri(request, currentPage);
                log.info("GetMatch search URL: {}", uri);
                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
                Map<String, Object> body = response.getBody();
                if (body == null) {
                    break;
                }

                Number foundRaw = body.get("found") instanceof Number ? (Number) body.get("found") : null;
                Number pagesRaw = body.get("pages") instanceof Number ? (Number) body.get("pages") : null;
                if (pagesRaw != null) {
                    totalPages = pagesRaw.intValue();
                }

                List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
                int size = items == null ? -1 : items.size();
                log.info("GetMatch response: status={}, found={}, items={}, page={}, pages={}",
                        response.getStatusCode(),
                        foundRaw == null ? null : foundRaw.intValue(),
                        size,
                        currentPage,
                        totalPages);

                if (items == null || items.isEmpty()) {
                    break;
                }

                List<Vacancy> pageVacancies = convertToVacancies(items, telegramId, cutoff);
                allVacancies.addAll(pageVacancies);
                if (!hasVacanciesNewerThan(items, cutoff)) {
                    break;
                }
                currentPage += 1;
            }

            return allVacancies;
        } catch (Exception e) {
            log.error("Error searching vacancies on GetMatch: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private java.net.URI buildSearchUri(SearchRequest request, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/vacancies")
                .queryParam("text", request.getQuery())
                .queryParam("period", request.getDays() == null ? 30 : request.getDays())
                .queryParam("per_page", 100)
                .queryParam("page", page)
                .queryParam("only_with_salary", false);

        if (request.getCityId() != null && !request.getCityId().isBlank()) {
            builder.queryParam("area", request.getCityId());
        } else if (request.getCountries() != null && !request.getCountries().isEmpty()) {
            if (request.getCountries().contains("russia")) {
                builder.queryParam("area", 113);
            }
            if (request.getCountries().contains("belarus")) {
                builder.queryParam("area", 16);
            }
        }
        if (request.getWorkTypes() != null) {
            for (String workType : request.getWorkTypes()) {
                builder.queryParam("work_format", workType);
            }
        }
        return builder.build().encode(java.nio.charset.StandardCharsets.UTF_8).toUri();
    }

    private List<Vacancy> convertToVacancies(List<Map<String, Object>> items, Long telegramId, LocalDateTime cutoff) {
        List<Vacancy> vacancies = new ArrayList<>();
        if (items == null) {
            return vacancies;
        }

        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (Map<String, Object> item : items) {
            try {
                Vacancy vacancy = new Vacancy();
                vacancy.setId(item.get("id").toString());
                vacancy.setUserTelegramId(telegramId);
                vacancy.setTitle(item.get("name") == null ? null : item.get("name").toString());

                Map<String, Object> employer = (Map<String, Object>) item.get("employer");
                if (employer != null) {
                    vacancy.setEmployer(employer.get("name") == null ? null : employer.get("name").toString());
                }

                Map<String, Object> area = (Map<String, Object>) item.get("area");
                if (area != null) {
                    vacancy.setCity(normalizeCity(area.get("name") == null ? null : area.get("name").toString()));
                }

                vacancy.setSchedule(extractWorkFormatLabel(item));

                Map<String, Object> salary = (Map<String, Object>) item.get("salary");
                vacancy.setSalary(formatSalary(salary));

                String publishedAt = item.get("published_at") == null ? null : item.get("published_at").toString();
                if (publishedAt != null) {
                    LocalDateTime publishedDateTime = OffsetDateTime.parse(publishedAt, sourceDateFormatter).toLocalDateTime();
                    if (cutoff != null && publishedDateTime.isBefore(cutoff)) {
                        skippedCount++;
                        continue;
                    }
                    vacancy.setPublishedAt(publishedDateTime);
                } else {
                    vacancy.setPublishedAt(LocalDateTime.now());
                }

                vacancy.setUrl(item.get("alternate_url") == null ? null : item.get("alternate_url").toString());
                vacancies.add(vacancy);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.warn("Error converting GetMatch vacancy item '{}': {}", item.get("name"), e.getMessage());
            }
        }

        log.info("Successfully converted {}/{} vacancies from GetMatch response", successCount, items.size());
        if (skippedCount > 0) {
            log.info("Skipped {} GetMatch vacancies older than cutoff", skippedCount);
        }
        if (errorCount > 0) {
            log.warn("Failed to convert {} GetMatch vacancies due to errors", errorCount);
        }
        return vacancies;
    }

    private boolean hasVacanciesNewerThan(List<Map<String, Object>> items, LocalDateTime cutoff) {
        if (cutoff == null) {
            return true;
        }
        for (Map<String, Object> item : items) {
            try {
                String publishedAt = item.get("published_at") == null ? null : item.get("published_at").toString();
                if (publishedAt == null) {
                    continue;
                }
                LocalDateTime publishedDateTime = OffsetDateTime.parse(publishedAt, sourceDateFormatter).toLocalDateTime();
                if (!publishedDateTime.isBefore(cutoff)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("Failed to parse GetMatch date for freshness check: {}", e.getMessage());
            }
        }
        return false;
    }

    private String extractWorkFormatLabel(Map<String, Object> item) {
        Object workFormatObj = item.get("work_format");
        if (!(workFormatObj instanceof List<?> workFormats)) {
            return null;
        }
        List<String> labels = new ArrayList<>();
        for (Object raw : workFormats) {
            if (!(raw instanceof Map<?, ?> wf)) {
                continue;
            }
            String id = wf.get("id") == null ? "" : wf.get("id").toString();
            switch (id) {
                case "REMOTE" -> labels.add("Удалённо");
                case "HYBRID" -> labels.add("Гибрид");
                case "ON_SITE" -> labels.add("Офис");
                default -> {
                    if (wf.get("name") != null) {
                        labels.add(wf.get("name").toString());
                    }
                }
            }
        }
        return labels.isEmpty() ? null : String.join(", ", labels.stream().distinct().toList());
    }

    private String normalizeCity(String city) {
        if (!StringUtils.hasText(city)) {
            return city;
        }
        String trimmed = city.trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }

        String firstPart = trimmed.split(",")[0].trim();
        if (firstPart.length() <= 100) {
            return firstPart;
        }

        return firstPart.substring(0, 100);
    }

    private String formatSalary(Map<String, Object> salary) {
        if (salary == null) {
            return "Не указана";
        }
        try {
            String from = salary.get("from") == null ? "" : salary.get("from").toString();
            String to = salary.get("to") == null ? "" : salary.get("to").toString();
            String currency = salary.get("currency") == null ? "" : salary.get("currency").toString();
            if (!from.isEmpty() && !to.isEmpty()) {
                return from + " - " + to + " " + currency;
            }
            if (!from.isEmpty()) {
                return "от " + from + " " + currency;
            }
            if (!to.isEmpty()) {
                return "до " + to + " " + currency;
            }
        } catch (Exception e) {
            log.debug("Error formatting GetMatch salary: {}", e.getMessage());
        }
        return "Не указана";
    }
}
