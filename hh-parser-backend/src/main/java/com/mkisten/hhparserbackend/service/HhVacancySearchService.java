package com.mkisten.hhparserbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkisten.hhparserbackend.entity.ScrapedVacancy;
import com.mkisten.hhparserbackend.entity.SearchPageCache;
import com.mkisten.hhparserbackend.entity.SearchProfile;
import com.mkisten.hhparserbackend.repository.ScrapedVacancyRepository;
import com.mkisten.hhparserbackend.repository.SearchPageCacheRepository;
import com.mkisten.hhparserbackend.repository.SearchProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HhVacancySearchService {

    private static final Pattern VACANCY_ID_PATTERN = Pattern.compile("/vacancy/(\\d+)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d[\\d\\s]*)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final DateTimeFormatter HH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final ZoneId HH_ZONE = ZoneId.of("Europe/Moscow");
    private static final TypeReference<List<Map<String, Object>>> ITEM_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> PARAMS_TYPE = new TypeReference<>() {};
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3), Map.entry("апреля", 4),
            Map.entry("мая", 5), Map.entry("июня", 6), Map.entry("июля", 7), Map.entry("августа", 8),
            Map.entry("сентября", 9), Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12)
    );

    private final ScrapedVacancyRepository scrapedVacancyRepository;
    private final SearchProfileRepository searchProfileRepository;
    private final SearchPageCacheRepository searchPageCacheRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.hh.base-url}")
    private String baseUrl;

    @Value("${app.hh.search-url}")
    private String searchUrl;

    @Value("${app.hh.timeout-ms}")
    private int timeoutMs;

    @Value("${app.hh.user-agent}")
    private String userAgent;

    @Value("${app.hh.default-area-russia}")
    private String defaultAreaRussia;

    @Value("${app.hh.default-area-belarus}")
    private String defaultAreaBelarus;

    @Value("${app.cache.ttl-minutes:30}")
    private int cacheTtlMinutes;

    @Value("${app.prefetch.enabled:true}")
    private boolean prefetchEnabled;

    @Value("${app.prefetch.max-pages:20}")
    private int prefetchMaxPages;

    @Value("${app.prefetch.recent-request-window-minutes:1440}")
    private int prefetchRecentWindowMinutes;

    @Value("${app.prefetch.request-delay-ms:250}")
    private long prefetchRequestDelayMs;

    public Map<String, Object> search(MultiValueMap<String, String> params) {
        SearchCriteria criteria = normalizeCriteria(params);
        registerProfile(criteria);

        ApiSearchResult cached = loadFreshPageCache(criteria);
        if (cached != null) {
            return toResponse(cached);
        }

        try {
            ApiSearchResult live = crawlApi(criteria);
            savePageCache(criteria, live);
            return toResponse(live);
        } catch (Exception e) {
            log.warn("HH HTML crawl failed, returning cache fallback: {}", e.getMessage());
            ApiSearchResult staleExact = loadLatestPageCache(criteria);
            if (staleExact != null) {
                return toResponse(staleExact);
            }
            return toResponse(fallbackFromVacancyCache(criteria));
        }
    }

    public void prefetchDueProfiles() {
        if (!prefetchEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now(HH_ZONE).minusMinutes(prefetchRecentWindowMinutes);
        List<SearchProfile> profiles = searchProfileRepository.findByEnabledTrueAndLastRequestedAtAfterOrderByLastRequestedAtDesc(cutoff);
        for (SearchProfile profile : profiles) {
            prefetchProfile(profile);
        }
    }

    @Transactional
    protected void prefetchProfile(SearchProfile profile) {
        try {
            SearchCriteria baseCriteria = criteriaFromProfile(profile);
            int maxPages = Math.max(1, prefetchMaxPages);
            int discoveredPages = maxPages;
            for (int page = 0; page < Math.min(maxPages, discoveredPages); page++) {
                SearchCriteria pageCriteria = baseCriteria.withPage(page);
                ApiSearchResult result = crawlApi(pageCriteria);
                savePageCache(pageCriteria, result);
                discoveredPages = Math.min(maxPages, Math.max(result.pages(), page + 1));
                if (result.items().isEmpty()) {
                    break;
                }
                sleepQuietly(prefetchRequestDelayMs);
            }
            profile.setLastPrefetchedAt(LocalDateTime.now(HH_ZONE));
            profile.setLastSuccessAt(LocalDateTime.now(HH_ZONE));
            profile.setFailureCount(0);
            profile.setLastError(null);
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            profile.setLastPrefetchedAt(LocalDateTime.now(HH_ZONE));
            profile.setFailureCount(profile.getFailureCount() + 1);
            profile.setLastError(limit(e.getMessage(), 1000));
            searchProfileRepository.save(profile);
            log.warn("Background prefetch failed for {}: {}", profile.getCacheKey(), e.getMessage());
        }
    }

    private ApiSearchResult crawlApi(SearchCriteria criteria) throws IOException {
        SearchResult live = crawl(criteria);
        List<Map<String, Object>> items = live.items().stream().map(this::toApiItem).toList();
        return new ApiSearchResult(live.found(), live.pages(), items);
    }

    @Transactional
    protected SearchResult crawl(SearchCriteria criteria) throws IOException {
        URI uri = buildSearchUri(criteria);
        log.info("HH parser request URL: {}", uri);

        Document document = Jsoup.connect(uri.toString())
                .userAgent(userAgent)
                .referrer(baseUrl)
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();

        List<ScrapedVacancy> parsedItems = parseCards(document);
        if (criteria.onlyWithSalary()) {
            parsedItems = parsedItems.stream().filter(item -> item.getSalaryFrom() != null || item.getSalaryTo() != null).toList();
        }
        if (criteria.period() != null && criteria.period() > 0) {
            LocalDateTime cutoff = LocalDateTime.now(HH_ZONE).minusDays(criteria.period());
            parsedItems = parsedItems.stream().filter(item -> item.getPublishedAt() == null || !item.getPublishedAt().isBefore(cutoff)).toList();
        }

        List<ScrapedVacancy> persisted = upsert(parsedItems);
        long found = parseFound(document).orElse((long) persisted.size());
        int actualPageSize = Math.max(persisted.size(), 1);
        int pages = found > 0 ? (int) Math.ceil((double) found / actualPageSize) : (persisted.isEmpty() ? 0 : criteria.page() + 1);
        return new SearchResult(found, pages, persisted);
    }

    private void registerProfile(SearchCriteria criteria) {
        try {
            SearchProfile profile = searchProfileRepository.findByCacheKey(criteria.cacheKey()).orElseGet(SearchProfile::new);
            profile.setCacheKey(criteria.cacheKey());
            profile.setParamsJson(objectMapper.writeValueAsString(criteria.paramsForStorage()));
            profile.setQueryText(criteria.text());
            profile.setAreas(String.join(",", criteria.areas()));
            profile.setEnabled(true);
            profile.setLastRequestedAt(LocalDateTime.now(HH_ZONE));
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            log.warn("Failed to register parser profile {}: {}", criteria.cacheKey(), e.getMessage());
        }
    }

    private ApiSearchResult loadFreshPageCache(SearchCriteria criteria) {
        ApiSearchResult cached = loadLatestPageCache(criteria);
        if (cached == null) {
            return null;
        }
        LocalDateTime cutoff = LocalDateTime.now(HH_ZONE).minusMinutes(cacheTtlMinutes);
        Optional<SearchPageCache> pageCache = searchPageCacheRepository.findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(criteria.cacheKey(), criteria.page());
        if (pageCache.isPresent() && !pageCache.get().getFetchedAt().isBefore(cutoff)) {
            return cached;
        }
        return null;
    }

    private ApiSearchResult loadLatestPageCache(SearchCriteria criteria) {
        try {
            Optional<SearchPageCache> cache = searchPageCacheRepository.findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(criteria.cacheKey(), criteria.page());
            if (cache.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> items = objectMapper.readValue(cache.get().getItemsJson(), ITEM_LIST_TYPE);
            return new ApiSearchResult(cache.get().getFoundCount(), cache.get().getPagesCount(), items);
        } catch (Exception e) {
            log.warn("Failed to load page cache {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
            return null;
        }
    }

    @Transactional
    protected void savePageCache(SearchCriteria criteria, ApiSearchResult result) {
        try {
            SearchPageCache cache = searchPageCacheRepository.findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(criteria.cacheKey(), criteria.page()).orElseGet(SearchPageCache::new);
            cache.setCacheKey(criteria.cacheKey());
            cache.setPageNumber(criteria.page());
            cache.setFoundCount(result.found());
            cache.setPagesCount(result.pages());
            cache.setItemsJson(objectMapper.writeValueAsString(result.items()));
            cache.setItemCount(result.items().size());
            cache.setFetchedAt(LocalDateTime.now(HH_ZONE));
            searchPageCacheRepository.save(cache);
            searchPageCacheRepository.deleteByCacheKeyAndFetchedAtBefore(criteria.cacheKey(), LocalDateTime.now(HH_ZONE).minus(Duration.ofDays(2)));
        } catch (Exception e) {
            log.warn("Failed to save page cache {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
        }
    }

    private ApiSearchResult fallbackFromVacancyCache(SearchCriteria criteria) {
        PageRequest pageRequest = PageRequest.of(Math.max(criteria.page(), 0), criteria.requestedPerPage());
        Page<ScrapedVacancy> cachedPage = criteria.text() == null
                ? scrapedVacancyRepository.findAllByOrderByPublishedAtDesc(pageRequest)
                : scrapedVacancyRepository.findByTitleContainingIgnoreCaseOrEmployerNameContainingIgnoreCaseOrderByPublishedAtDesc(criteria.text(), criteria.text(), pageRequest);
        List<ScrapedVacancy> items = cachedPage.getContent();
        if (criteria.onlyWithSalary()) {
            items = items.stream().filter(item -> item.getSalaryFrom() != null || item.getSalaryTo() != null).toList();
        }
        return new ApiSearchResult(cachedPage.getTotalElements(), cachedPage.getTotalPages(), items.stream().map(this::toApiItem).toList());
    }

    private SearchCriteria criteriaFromProfile(SearchProfile profile) throws IOException {
        Map<String, List<String>> paramsMap = objectMapper.readValue(profile.getParamsJson(), PARAMS_TYPE);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        paramsMap.forEach((k, v) -> params.put(k, new ArrayList<>(v)));
        return normalizeCriteria(params);
    }

    private SearchCriteria normalizeCriteria(MultiValueMap<String, String> params) {
        String text = normalizeBlank(params.getFirst("text"));
        int page = Math.max(parseInt(params.getFirst("page"), 0), 0);
        int requestedPerPage = clamp(parseInt(params.getFirst("per_page"), 20), 1, 100);
        boolean onlyWithSalary = Boolean.parseBoolean(Optional.ofNullable(params.getFirst("only_with_salary")).orElse("false"));
        Integer period = parseNullableInt(params.getFirst("period"));
        String searchField = normalizeBlank(params.getFirst("search_field"));
        if (searchField == null) {
            searchField = "name";
        }
        List<String> areas = normalizeList(params.get("area"));
        if (areas.isEmpty()) {
            areas = resolveAreasFromCountries(params);
        }
        List<String> professionalRoles = normalizeList(params.get("professional_role"));
        List<String> schedules = normalizeList(params.get("schedule"));
        List<String> workFormats = normalizeList(params.get("work_format"));

        Map<String, List<String>> paramsForStorage = new TreeMap<>();
        putIfNotEmpty(paramsForStorage, "text", text == null ? List.of() : List.of(text));
        putIfNotEmpty(paramsForStorage, "area", areas);
        putIfNotEmpty(paramsForStorage, "professional_role", professionalRoles);
        putIfNotEmpty(paramsForStorage, "schedule", schedules);
        putIfNotEmpty(paramsForStorage, "work_format", workFormats);
        putIfNotEmpty(paramsForStorage, "search_field", List.of(searchField));
        putIfNotEmpty(paramsForStorage, "only_with_salary", List.of(Boolean.toString(onlyWithSalary)));
        if (period != null) {
            putIfNotEmpty(paramsForStorage, "period", List.of(Integer.toString(period)));
        }

        String cacheKey = buildCacheKey(text, areas, professionalRoles, schedules, workFormats, searchField, onlyWithSalary, period);
        return new SearchCriteria(text, areas, page, requestedPerPage, onlyWithSalary, period, searchField, professionalRoles, schedules, workFormats, cacheKey, paramsForStorage);
    }

    private URI buildSearchUri(SearchCriteria criteria) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(searchUrl)
                .queryParam("page", criteria.page())
                .queryParam("enable_snippets", true)
                .queryParam("search_field", criteria.searchField());
        if (criteria.text() != null) {
            builder.queryParam("text", criteria.text());
        }
        criteria.areas().forEach(area -> builder.queryParam("area", area));
        criteria.professionalRoles().forEach(role -> builder.queryParam("professional_role", role));
        criteria.schedules().forEach(schedule -> builder.queryParam("schedule", schedule));
        criteria.workFormats().forEach(format -> builder.queryParam("work_format", format));
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private List<String> resolveAreasFromCountries(MultiValueMap<String, String> params) {
        List<String> countries = params.get("country");
        if (countries == null || countries.isEmpty()) {
            countries = params.get("countries");
        }
        if (countries == null || countries.isEmpty()) {
            return List.of();
        }
        String joined = String.join(",", countries).toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (joined.contains("russia") || joined.contains("рос")) {
            result.add(defaultAreaRussia);
        }
        if (joined.contains("belarus") || joined.contains("бел")) {
            result.add(defaultAreaBelarus);
        }
        return result;
    }

    private List<ScrapedVacancy> parseCards(Document document) {
        Set<Element> cards = new LinkedHashSet<>();
        List<String> selectors = List.of("[data-qa='serp-item']", "[data-qa='vacancy-serp__vacancy']", ".serp-item", ".vacancy-serp-item");
        for (String selector : selectors) {
            cards.addAll(document.select(selector));
        }
        List<ScrapedVacancy> vacancies = new ArrayList<>();
        for (Element card : cards) {
            ScrapedVacancy vacancy = parseCard(card);
            if (vacancy != null) {
                vacancies.add(vacancy);
            }
        }
        vacancies.sort(Comparator.comparing(ScrapedVacancy::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return vacancies;
    }

    private ScrapedVacancy parseCard(Element card) {
        Element titleLink = first(card, "a[data-qa='serp-item__title']", "[data-qa='serp-item__title']", "a[href*='/vacancy/']");
        if (titleLink == null) {
            return null;
        }
        String href = normalizeBlank(titleLink.absUrl("href"));
        if (href == null) {
            href = normalizeBlank(titleLink.attr("href"));
        }
        if (href == null) {
            return null;
        }
        String externalId = extractVacancyId(href);
        if (externalId == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(HH_ZONE);
        ScrapedVacancy vacancy = new ScrapedVacancy();
        vacancy.setExternalId(externalId);
        vacancy.setTitle(limit(normalizeBlank(titleLink.text()), 512));
        vacancy.setAlternateUrl(href);
        vacancy.setEmployerName(limit(firstText(card, "[data-qa='vacancy-serp__vacancy-employer']", "[data-qa='vacancy-serp__vacancy-employer-text']", "[data-qa='vacancy-serp__vacancy-company']"), 255));
        vacancy.setAreaName(limit(firstText(card, "[data-qa='vacancy-serp__vacancy-address']", "[data-qa='vacancy-serp__vacancy-address-text']", "[data-qa='vacancy-serp__vacancy-work-address']"), 255));

        String salaryText = normalizeBlank(firstText(card, "[data-qa='vacancy-serp__vacancy-compensation']", "[data-qa='vacancy-serp__vacancy-salary']"));
        vacancy.setSalaryText(limit(salaryText, 512));
        applySalary(vacancy, salaryText);

        String rawPublishedText = normalizeBlank(firstText(card, "[data-qa='vacancy-serp__vacancy-date']", "[data-qa='vacancy-serp__publication-date']", "[data-qa='vacancy-serp__vacancy-date-info']"));
        vacancy.setRawPublishedText(limit(rawPublishedText, 255));
        vacancy.setPublishedAt(parsePublishedAt(rawPublishedText));

        String snippetRequirement = normalizeBlank(firstText(card, "[data-qa='vacancy-serp__vacancy_snippet_requirement']", "[data-qa='vacancy-serp__vacancy-requirement']"));
        String snippetResponsibility = normalizeBlank(firstText(card, "[data-qa='vacancy-serp__vacancy_snippet_responsibility']", "[data-qa='vacancy-serp__vacancy-responsibility']"));
        if (snippetRequirement == null && snippetResponsibility == null) {
            String combinedSnippet = normalizeBlank(firstText(card, "[data-qa='vacancy-serp__vacancy_snippet']", ".vacancy-serp-item-body__main-info"));
            if (combinedSnippet != null) {
                String[] parts = combinedSnippet.split("[\\r\\n]+", 2);
                snippetRequirement = parts.length > 0 ? normalizeBlank(parts[0]) : null;
                snippetResponsibility = parts.length > 1 ? normalizeBlank(parts[1]) : null;
            }
        }
        vacancy.setSnippetRequirement(limit(snippetRequirement, 4000));
        vacancy.setSnippetResponsibility(limit(snippetResponsibility, 4000));

        applyWorkFormat(vacancy, card.text());
        vacancy.setFirstSeenAt(now);
        vacancy.setLastSeenAt(now);
        if (vacancy.getPublishedAt() == null) {
            vacancy.setPublishedAt(now);
        }
        return vacancy;
    }

    @Transactional
    protected List<ScrapedVacancy> upsert(List<ScrapedVacancy> parsedItems) {
        if (parsedItems.isEmpty()) {
            return List.of();
        }
        Map<String, ScrapedVacancy> existingByExternalId = scrapedVacancyRepository.findByExternalIdIn(parsedItems.stream().map(ScrapedVacancy::getExternalId).toList())
                .stream().collect(Collectors.toMap(ScrapedVacancy::getExternalId, item -> item));
        List<ScrapedVacancy> toSave = new ArrayList<>();
        for (ScrapedVacancy parsed : parsedItems) {
            ScrapedVacancy target = existingByExternalId.get(parsed.getExternalId());
            if (target == null) {
                toSave.add(parsed);
                continue;
            }
            merge(target, parsed);
            toSave.add(target);
        }
        return scrapedVacancyRepository.saveAll(toSave);
    }

    private void merge(ScrapedVacancy target, ScrapedVacancy parsed) {
        target.setTitle(parsed.getTitle());
        target.setAlternateUrl(parsed.getAlternateUrl());
        target.setEmployerName(parsed.getEmployerName());
        target.setAreaName(parsed.getAreaName());
        target.setSalaryText(parsed.getSalaryText());
        target.setSalaryFrom(parsed.getSalaryFrom());
        target.setSalaryTo(parsed.getSalaryTo());
        target.setSalaryCurrency(parsed.getSalaryCurrency());
        target.setScheduleName(parsed.getScheduleName());
        target.setWorkFormatId(parsed.getWorkFormatId());
        target.setWorkFormatName(parsed.getWorkFormatName());
        target.setSnippetRequirement(parsed.getSnippetRequirement());
        target.setSnippetResponsibility(parsed.getSnippetResponsibility());
        target.setRawPublishedText(parsed.getRawPublishedText());
        target.setPublishedAt(parsed.getPublishedAt());
        target.setLastSeenAt(parsed.getLastSeenAt());
    }

    private Optional<Long> parseFound(Document document) {
        List<String> selectors = List.of("[data-qa='vacancies-search-header']", "[data-qa='vacancies-total-found']", ".bloko-header-section-2");
        for (String selector : selectors) {
            for (Element element : document.select(selector)) {
                Matcher matcher = NUMBER_PATTERN.matcher(element.text().replace('\u00A0', ' '));
                if (matcher.find()) {
                    String digits = matcher.group(1).replaceAll("\\s+", "");
                    try {
                        return Optional.of(Long.parseLong(digits));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> toApiItem(ScrapedVacancy vacancy) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", vacancy.getExternalId());
        item.put("name", vacancy.getTitle());
        item.put("alternate_url", vacancy.getAlternateUrl());
        item.put("published_at", formatPublishedAt(vacancy.getPublishedAt()));
        item.put("employer", namedMap(vacancy.getEmployerName()));
        item.put("area", namedMap(vacancy.getAreaName()));
        item.put("schedule", namedMap(vacancy.getScheduleName()));
        if (vacancy.getWorkFormatId() != null) {
            Map<String, Object> workFormat = new LinkedHashMap<>();
            workFormat.put("id", vacancy.getWorkFormatId());
            workFormat.put("name", vacancy.getWorkFormatName());
            item.put("work_format", List.of(workFormat));
        } else {
            item.put("work_format", List.of());
        }
        if (vacancy.getSalaryFrom() != null || vacancy.getSalaryTo() != null || vacancy.getSalaryCurrency() != null) {
            Map<String, Object> salary = new LinkedHashMap<>();
            salary.put("from", vacancy.getSalaryFrom());
            salary.put("to", vacancy.getSalaryTo());
            salary.put("currency", vacancy.getSalaryCurrency());
            item.put("salary", salary);
        } else {
            item.put("salary", null);
        }
        Map<String, Object> snippet = new LinkedHashMap<>();
        snippet.put("requirement", vacancy.getSnippetRequirement());
        snippet.put("responsibility", vacancy.getSnippetResponsibility());
        item.put("snippet", snippet);
        return item;
    }

    private Map<String, Object> namedMap(String value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", value);
        return map;
    }

    private Map<String, Object> toResponse(ApiSearchResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", result.found());
        response.put("pages", result.pages());
        response.put("items", result.items());
        return response;
    }

    private String formatPublishedAt(LocalDateTime publishedAt) {
        LocalDateTime value = publishedAt != null ? publishedAt : LocalDateTime.now(HH_ZONE);
        return value.atZone(HH_ZONE).format(HH_DATE_FORMATTER);
    }

    private String buildCacheKey(String text, List<String> areas, List<String> roles, List<String> schedules, List<String> workFormats,
                                 String searchField, boolean onlyWithSalary, Integer period) {
        return String.join("|",
                "text=" + Optional.ofNullable(text).orElse(""),
                "areas=" + String.join(",", areas),
                "roles=" + String.join(",", roles),
                "schedules=" + String.join(",", schedules),
                "workFormats=" + String.join(",", workFormats),
                "searchField=" + searchField,
                "onlyWithSalary=" + onlyWithSalary,
                "period=" + Optional.ofNullable(period).map(String::valueOf).orElse("")
        );
    }

    private void putIfNotEmpty(Map<String, List<String>> target, String key, Collection<String> values) {
        List<String> cleaned = values.stream().filter(Objects::nonNull).map(this::normalizeBlank).filter(Objects::nonNull).toList();
        if (!cleaned.isEmpty()) {
            target.put(key, cleaned);
        }
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::normalizeBlank).filter(Objects::nonNull).distinct().toList();
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void applySalary(ScrapedVacancy vacancy, String salaryText) {
        if (salaryText == null) {
            return;
        }
        String normalized = salaryText.replace(' ', ' ').replace(',', '.');
        List<Integer> numbers = INTEGER_PATTERN.matcher(normalized).results().map(match -> match.group().replaceAll("\\s+", "")).map(Integer::valueOf).toList();
        if (normalized.contains("от") && !numbers.isEmpty()) {
            vacancy.setSalaryFrom(numbers.get(0));
        } else if (normalized.contains("до") && !numbers.isEmpty()) {
            vacancy.setSalaryTo(numbers.get(0));
        } else if (numbers.size() >= 2) {
            vacancy.setSalaryFrom(numbers.get(0));
            vacancy.setSalaryTo(numbers.get(1));
        } else if (numbers.size() == 1) {
            vacancy.setSalaryFrom(numbers.get(0));
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("₽") || lower.contains("руб")) {
            vacancy.setSalaryCurrency("RUR");
        } else if (lower.contains("$") || lower.contains("usd")) {
            vacancy.setSalaryCurrency("USD");
        } else if (lower.contains("€") || lower.contains("eur")) {
            vacancy.setSalaryCurrency("EUR");
        } else if (lower.contains("kzt") || lower.contains("₸")) {
            vacancy.setSalaryCurrency("KZT");
        } else if (lower.contains("byn") || lower.contains("бел")) {
            vacancy.setSalaryCurrency("BYN");
        }
    }

    private void applyWorkFormat(ScrapedVacancy vacancy, String cardText) {
        String lower = Optional.ofNullable(cardText).orElse("").toLowerCase(Locale.ROOT);
        if (lower.contains("удал")) {
            vacancy.setWorkFormatId("REMOTE");
            vacancy.setWorkFormatName("Удалённо");
            vacancy.setScheduleName("Удалённая работа");
            return;
        }
        if (lower.contains("гибрид")) {
            vacancy.setWorkFormatId("HYBRID");
            vacancy.setWorkFormatName("Гибрид");
            vacancy.setScheduleName("Гибрид");
            return;
        }
        if (vacancy.getAreaName() != null) {
            vacancy.setWorkFormatId("ON_SITE");
            vacancy.setWorkFormatName("Офис");
            vacancy.setScheduleName("Офис");
        }
    }

    private LocalDateTime parsePublishedAt(String rawPublishedText) {
        LocalDateTime now = LocalDateTime.now(HH_ZONE);
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

    private String extractVacancyId(String url) {
        Matcher matcher = VACANCY_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
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

    private Integer parseNullableInt(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : parseInt(normalized, 0);
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SearchResult(long found, int pages, List<ScrapedVacancy> items) {
    }

    private record ApiSearchResult(long found, int pages, List<Map<String, Object>> items) {
    }

    private record SearchCriteria(
            String text,
            List<String> areas,
            int page,
            int requestedPerPage,
            boolean onlyWithSalary,
            Integer period,
            String searchField,
            List<String> professionalRoles,
            List<String> schedules,
            List<String> workFormats,
            String cacheKey,
            Map<String, List<String>> paramsForStorage
    ) {
        private SearchCriteria withPage(int nextPage) {
            return new SearchCriteria(text, areas, nextPage, requestedPerPage, onlyWithSalary, period, searchField,
                    professionalRoles, schedules, workFormats, cacheKey, paramsForStorage);
        }
    }
}
