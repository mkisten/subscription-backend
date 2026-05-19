package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.CityDto;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
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
public class SuperjobCareerApiService {

    private static final Map<String, String> HH_COUNTRY_ID_TO_KEY = Map.of(
            "113", "russia",
            "16", "belarus"
    );

    private static final Map<String, Map<String, String>> SUPERJOB_TOWNS_BY_COUNTRY_AND_CITY = Map.of(
            "russia", Map.ofEntries(
                    Map.entry("москва", "4"),
                    Map.entry("санкт петербург", "14"),
                    Map.entry("новосибирск", "13"),
                    Map.entry("екатеринбург", "33"),
                    Map.entry("казань", "55"),
                    Map.entry("нижний новгород", "12"),
                    Map.entry("самара", "5"),
                    Map.entry("краснодар", "25"),
                    Map.entry("ростов на дону", "73"),
                    Map.entry("сочи", "745"),
                    Map.entry("уфа", "173"),
                    Map.entry("пермь", "119"),
                    Map.entry("воронеж", "42"),
                    Map.entry("красноярск", "130"),
                    Map.entry("челябинск", "106"),
                    Map.entry("омск", "17"),
                    Map.entry("саратов", "146"),
                    Map.entry("волгоград", "89"),
                    Map.entry("тюмень", "168"),
                    Map.entry("иннополис", "3480")
            ),
            "belarus", Map.ofEntries(
                    Map.entry("минск", "430"),
                    Map.entry("брест", "433"),
                    Map.entry("витебск", "434"),
                    Map.entry("гомель", "435"),
                    Map.entry("гродно", "436"),
                    Map.entry("могилев", "438")
            )
    );

    private final RestTemplate restTemplate;
    private final AuthServiceClient authServiceClient;
    private final HHruAreaService hhruAreaService;

    @Value("${app.superjob.base-url}")
    private String baseUrl;

    @Value("${app.superjob.max-pages:5}")
    private int maxPages;

    @Value("${app.superjob.enabled:false}")
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
            CityDto resolvedCity = hhruAreaService.findCityById(request.getCityId());
            List<String> countries = resolveCountries(request, resolvedCity);

            List<Vacancy> allVacancies = new ArrayList<>();
            for (String country : countries) {
                List<Vacancy> countryVacancies = searchCountryVacancies(request, telegramId, cutoff, resolvedCity, country);
                allVacancies.addAll(countryVacancies);
            }

            return allVacancies;
        } catch (Exception e) {
            log.error("Error searching vacancies on SuperJob: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<Vacancy> searchCountryVacancies(
            SearchRequest request,
            Long telegramId,
            LocalDateTime cutoff,
            CityDto resolvedCity,
            String country
    ) {
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
                log.info("SuperJob pagination stopped at maxPages={} for country={}", maxPages, country);
                break;
            }

            java.net.URI uri = buildSearchUri(request, currentPage, country, resolvedCity);
            log.info("SuperJob search URL: {}", uri);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                break;
            }

            Number pagesRaw = body.get("pages") instanceof Number ? (Number) body.get("pages") : null;
            if (pagesRaw != null) {
                totalPages = pagesRaw.intValue();
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty()) {
                break;
            }

            List<Vacancy> pageVacancies = convertToVacancies(items, telegramId, cutoff, request.getQuery());
            allVacancies.addAll(pageVacancies);
            if (!hasVacanciesNewerThan(items, cutoff)) {
                break;
            }
            currentPage += 1;
        }

        return allVacancies;
    }

    private java.net.URI buildSearchUri(SearchRequest request, int page, String country, CityDto resolvedCity) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/vacancies")
                .queryParam("text", request.getQuery())
                .queryParam("period", request.getDays() == null ? 30 : request.getDays())
                .queryParam("per_page", 100)
                .queryParam("page", page)
                .queryParam("only_with_salary", false);

        if (country != null && !country.isBlank()) {
            builder.queryParam("country", country);
        }
        if (resolvedCity != null && StringUtils.hasText(resolvedCity.getName())) {
            builder.queryParam("city_name", resolvedCity.getName());
            String town = resolveTownId(country, resolvedCity.getName());
            if (town != null) {
                builder.queryParam("town", town);
            }
        }
        if (request.getWorkTypes() != null) {
            for (String workType : request.getWorkTypes()) {
                builder.queryParam("work_format", workType);
            }
        }
        return builder.build().encode(java.nio.charset.StandardCharsets.UTF_8).toUri();
    }

    private List<String> resolveCountries(SearchRequest request, CityDto resolvedCity) {
        List<String> result = new ArrayList<>();
        if (resolvedCity != null) {
            String country = HH_COUNTRY_ID_TO_KEY.get(resolvedCity.getCountryId());
            if (country != null) {
                result.add(country);
                return result;
            }
        }

        if (request.getCountries() != null) {
            for (String country : request.getCountries()) {
                if (country == null) {
                    continue;
                }
                String normalized = country.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals("russia") || normalized.equals("belarus")) {
                    result.add(normalized);
                }
            }
        }
        if (result.isEmpty()) {
            result.add("russia");
        }
        return result.stream().distinct().toList();
    }

    private String resolveTownId(String country, String cityName) {
        if (!StringUtils.hasText(country) || !StringUtils.hasText(cityName)) {
            return null;
        }
        Map<String, String> towns = SUPERJOB_TOWNS_BY_COUNTRY_AND_CITY.get(country);
        if (towns == null) {
            return null;
        }
        return towns.get(normalizeCityKey(cityName));
    }

    private List<Vacancy> convertToVacancies(List<Map<String, Object>> items, Long telegramId, LocalDateTime cutoff, String query) {
        List<Vacancy> vacancies = new ArrayList<>();
        if (items == null) {
            return vacancies;
        }

        for (Map<String, Object> item : items) {
            try {
                String title = item.get("name") == null ? null : item.get("name").toString();
                if (!matchesQuery(item, query)) {
                    continue;
                }

                Vacancy vacancy = new Vacancy();
                vacancy.setId("superjob-" + item.get("id"));
                vacancy.setUserTelegramId(telegramId);
                vacancy.setTitle(title);

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
                        continue;
                    }
                    vacancy.setPublishedAt(publishedDateTime);
                } else {
                    vacancy.setPublishedAt(LocalDateTime.now());
                }

                vacancy.setUrl(item.get("alternate_url") == null ? null : item.get("alternate_url").toString());
                vacancies.add(vacancy);
            } catch (Exception e) {
                log.warn("Error converting SuperJob vacancy item '{}': {}", item.get("name"), e.getMessage());
            }
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
                log.debug("Failed to parse SuperJob date for freshness check: {}", e.getMessage());
            }
        }
        return false;
    }

    private String extractWorkFormatLabel(Map<String, Object> item) {
        Object workFormatObj = item.get("work_format");
        if (workFormatObj instanceof List<?> workFormats) {
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
            if (!labels.isEmpty()) {
                return String.join(", ", labels.stream().distinct().toList());
            }
        }

        Map<String, Object> schedule = (Map<String, Object>) item.get("schedule");
        if (schedule != null && schedule.get("name") != null) {
            return schedule.get("name").toString();
        }
        return null;
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
        return firstPart.length() <= 100 ? firstPart : firstPart.substring(0, 100);
    }

    private String normalizeCityKey(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
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
            log.debug("Error formatting SuperJob salary: {}", e.getMessage());
        }
        return "Не указана";
    }

    private boolean matchesQuery(Map<String, Object> item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        List<String> haystacks = new ArrayList<>();
        Object title = item.get("name");
        if (title instanceof String titleValue && !titleValue.isBlank()) {
            haystacks.add(titleValue);
        }

        Object employerRaw = item.get("employer");
        if (employerRaw instanceof Map<?, ?> employer && employer.get("name") instanceof String employerName && !employerName.isBlank()) {
            haystacks.add(employerName);
        }

        String normalizedQuery = normalizeForMatch(query);
        Object snippetRaw = item.get("snippet");
        if (snippetRaw instanceof Map<?, ?> snippet) {
            Object requirement = snippet.get("requirement");
            if (requirement instanceof String requirementText && !requirementText.isBlank()) {
                haystacks.add(requirementText);
            }
            Object responsibility = snippet.get("responsibility");
            if (responsibility instanceof String responsibilityText && !responsibilityText.isBlank()) {
                haystacks.add(responsibilityText);
            }
        }

        for (String haystack : haystacks) {
            String normalizedHaystack = normalizeForMatch(haystack);
            if (normalizedHaystack.contains(normalizedQuery)) {
                return true;
            }

            String[] tokens = normalizedQuery.split("\\s+");
            boolean hasToken = false;
            boolean allTokensPresent = true;
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                hasToken = true;
                if (!normalizedHaystack.contains(token)) {
                    allTokensPresent = false;
                    break;
                }
            }
            if (hasToken && allTokensPresent) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForMatch(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('/', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
