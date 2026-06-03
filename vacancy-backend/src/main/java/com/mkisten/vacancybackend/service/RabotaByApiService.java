package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.CityDto;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabotaByApiService {

    private static final String BELARUS_COUNTRY_ID = "16";
    private static final Pattern VACANCY_ID_PATTERN = Pattern.compile("/vacancy/(\\d+)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3), Map.entry("апреля", 4),
            Map.entry("мая", 5), Map.entry("июня", 6), Map.entry("июля", 7), Map.entry("августа", 8),
            Map.entry("сентября", 9), Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12)
    );
    private static final ZoneId RABOTA_BY_ZONE = ZoneId.of("Europe/Minsk");

    private final AuthServiceClient authServiceClient;
    private final RabotaByAreaService rabotaByAreaService;

    @Value("${app.rabota-by.enabled:false}")
    private boolean enabled;

    @Value("${app.rabota-by.base-url:https://rabota.by}")
    private String baseUrl;

    @Value("${app.rabota-by.search-url:https://rabota.by/search/vacancy}")
    private String searchUrl;

    @Value("${app.rabota-by.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${app.rabota-by.user-agent:Mozilla/5.0 (compatible; VacancyBot/1.0)}")
    private String userAgent;

    @Value("${app.rabota-by.max-pages:5}")
    private int maxPages;

    public List<Vacancy> searchVacancies(SearchRequest request, String token) {
        if (!enabled || !StringUtils.hasText(request.getQuery()) || !shouldUseRabotaBy(request)) {
            return new ArrayList<>();
        }
        try {
            ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
            Long telegramId = profile.getTelegramId();
            int days = request.getDays() != null ? request.getDays() : 30;
            LocalDateTime cutoff = LocalDateTime.now(RABOTA_BY_ZONE).minusDays(days);
            List<Vacancy> allVacancies = new ArrayList<>();
            Set<String> seenIds = new LinkedHashSet<>();

            for (int currentPage = 0; currentPage < maxPages; currentPage++) {
                URI uri = buildSearchUri(request, currentPage);
                log.info("Rabota.by search URL: {}", uri);

                Document document = Jsoup.connect(uri.toString())
                        .userAgent(userAgent)
                        .referrer(baseUrl)
                        .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .timeout(timeoutMs)
                        .followRedirects(true)
                        .get();

                List<Vacancy> pageVacancies = parseVacancies(document, telegramId, cutoff);
                if (pageVacancies.isEmpty()) {
                    break;
                }

                boolean hasFreshVacancies = false;
                for (Vacancy vacancy : pageVacancies) {
                    if (seenIds.add(vacancy.getId())) {
                        allVacancies.add(vacancy);
                    }
                    if (vacancy.getPublishedAt() == null || !vacancy.getPublishedAt().isBefore(cutoff)) {
                        hasFreshVacancies = true;
                    }
                }
                if (!hasFreshVacancies) {
                    break;
                }
            }

            return allVacancies;
        } catch (Exception e) {
            log.error("Error searching vacancies on Rabota.by: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private boolean shouldUseRabotaBy(SearchRequest request) {
        if (StringUtils.hasText(request.getCityId())) {
            CityDto city = rabotaByAreaService.findCityById(request.getCityId());
            return city != null && BELARUS_COUNTRY_ID.equals(city.getCountryId());
        }
        return request.getCountries() != null && request.getCountries().contains("belarus");
    }

    private URI buildSearchUri(SearchRequest request, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(searchUrl)
                .queryParam("text", request.getQuery())
                .queryParam("page", page)
                .queryParam("search_field", "name");

        if (StringUtils.hasText(request.getCityId())) {
            CityDto city = rabotaByAreaService.findCityById(request.getCityId());
            if (city != null) {
                builder.queryParam("area", city.getId());
            } else {
                builder.queryParam("area", BELARUS_COUNTRY_ID);
            }
        } else {
            builder.queryParam("area", BELARUS_COUNTRY_ID);
        }

        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private List<Vacancy> parseVacancies(Document document, Long telegramId, LocalDateTime cutoff) {
        Set<Element> cards = new LinkedHashSet<>();
        List<String> selectors = List.of(
                "[data-qa='serp-item']",
                "[data-qa='vacancy-serp__vacancy']",
                ".serp-item",
                ".vacancy-serp-item"
        );
        for (String selector : selectors) {
            cards.addAll(document.select(selector));
        }

        List<Vacancy> vacancies = new ArrayList<>();
        for (Element card : cards) {
            Vacancy vacancy = parseCard(card, telegramId);
            if (vacancy == null) {
                continue;
            }
            if (cutoff != null && vacancy.getPublishedAt() != null && vacancy.getPublishedAt().isBefore(cutoff)) {
                continue;
            }
            vacancies.add(vacancy);
        }
        vacancies.sort(Comparator.comparing(Vacancy::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return vacancies;
    }

    private Vacancy parseCard(Element card, Long telegramId) {
        Element titleLink = first(card,
                "a[data-qa='serp-item__title']",
                "[data-qa='serp-item__title']",
                "a[href*='/vacancy/']");
        if (titleLink == null) {
            return null;
        }

        String href = normalizeBlank(titleLink.absUrl("href"));
        if (href == null) {
            href = normalizeBlank(titleLink.attr("href"));
            if (href != null && href.startsWith("/")) {
                href = baseUrl + href;
            }
        }
        if (href == null) {
            return null;
        }

        String externalId = extractVacancyId(href);
        if (externalId == null) {
            return null;
        }

        Vacancy vacancy = new Vacancy();
        vacancy.setId("rabota-by-" + externalId);
        vacancy.setUserTelegramId(telegramId);
        vacancy.setTitle(limit(normalizeBlank(titleLink.text()), 500));
        vacancy.setEmployer(limit(firstText(card,
                "[data-qa='vacancy-serp__vacancy-employer']",
                "[data-qa='vacancy-serp__vacancy-employer-text']",
                "[data-qa='vacancy-serp__vacancy-company']"), 255));
        vacancy.setCity(limit(firstText(card,
                "[data-qa='vacancy-serp__vacancy-address']",
                "[data-qa='vacancy-serp__vacancy-address-text']",
                "[data-qa='vacancy-serp__vacancy-work-address']"), 100));

        String salaryText = normalizeBlank(firstText(card,
                "[data-qa='vacancy-serp__vacancy-compensation']",
                "[data-qa='vacancy-serp__vacancy-salary']"));
        vacancy.setSalary(limit(salaryText != null ? salaryText : "Не указана", 100));

        String rawPublishedText = normalizeBlank(firstText(card,
                "[data-qa='vacancy-serp__vacancy-date']",
                "[data-qa='vacancy-serp__publication-date']",
                "[data-qa='vacancy-serp__vacancy-date-info']"));
        vacancy.setPublishedAt(parsePublishedAt(rawPublishedText));
        vacancy.setSchedule(limit(extractWorkFormat(card.text()), 50));
        vacancy.setUrl(limit(href, 500));
        vacancy.setSource("Rabota.by");
        return vacancy;
    }

    private String extractWorkFormat(String cardText) {
        String lower = Optional.ofNullable(cardText).orElse("").toLowerCase(Locale.ROOT);
        if (lower.contains("удал")) {
            return "Удалённо";
        }
        if (lower.contains("гибрид")) {
            return "Гибрид";
        }
        if (lower.contains("офис") || lower.contains("в офис")) {
            return "Офис";
        }
        return null;
    }

    private LocalDateTime parsePublishedAt(String rawPublishedText) {
        LocalDateTime now = LocalDateTime.now(RABOTA_BY_ZONE);
        if (rawPublishedText == null) {
            return now;
        }
        String raw = rawPublishedText.trim().toLowerCase(Locale.ROOT);
        if (raw.contains("сегодня")) {
            return now.withSecond(0).withNano(0);
        }
        if (raw.contains("вчера")) {
            return now.minusDays(1).with(LocalTime.of(12, 0));
        }
        Matcher minuteMatcher = Pattern.compile("(\\d+)\\s+(минута|минуты|минут)").matcher(raw);
        if (minuteMatcher.find()) {
            return now.minusMinutes(Long.parseLong(minuteMatcher.group(1)));
        }
        Matcher hourMatcher = Pattern.compile("(\\d+)\\s+(час|часа|часов)").matcher(raw);
        if (hourMatcher.find()) {
            return now.minusHours(Long.parseLong(hourMatcher.group(1)));
        }
        Matcher dayMatcher = Pattern.compile("(\\d+)\\s+(день|дня|дней)").matcher(raw);
        if (dayMatcher.find()) {
            return now.minusDays(Long.parseLong(dayMatcher.group(1))).with(LocalTime.of(12, 0));
        }
        Matcher absoluteMatcher = Pattern.compile("(\\d{1,2})\\s+([а-я]+)").matcher(raw);
        if (absoluteMatcher.find()) {
            int day = Integer.parseInt(absoluteMatcher.group(1));
            Integer month = MONTHS.get(absoluteMatcher.group(2));
            if (month != null) {
                LocalDate date = LocalDate.of(now.getYear(), month, day);
                if (date.isAfter(now.toLocalDate().plusDays(1))) {
                    date = date.minusYears(1);
                }
                return date.atTime(12, 0);
            }
        }
        return now;
    }

    private String extractVacancyId(String url) {
        Matcher matcher = VACANCY_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Element first(Element root, String... selectors) {
        for (String selector : selectors) {
            Element found = root.selectFirst(selector);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String firstText(Element root, String... selectors) {
        Element element = first(root, selectors);
        return element != null ? normalizeBlank(element.text()) : null;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace(' ', ' ').trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
