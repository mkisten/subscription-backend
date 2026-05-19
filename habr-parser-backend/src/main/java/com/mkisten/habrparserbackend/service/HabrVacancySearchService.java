package com.mkisten.habrparserbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkisten.habrparserbackend.entity.ScrapedVacancy;
import com.mkisten.habrparserbackend.entity.SearchPageCache;
import com.mkisten.habrparserbackend.entity.SearchProfile;
import com.mkisten.habrparserbackend.repository.ScrapedVacancyRepository;
import com.mkisten.habrparserbackend.repository.SearchPageCacheRepository;
import com.mkisten.habrparserbackend.repository.SearchProfileRepository;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HabrVacancySearchService {

    private static final ZoneId HABR_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter HH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final TypeReference<List<Map<String, Object>>> ITEM_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> PARAMS_TYPE = new TypeReference<>() {};

    private final ScrapedVacancyRepository scrapedVacancyRepository;
    private final SearchProfileRepository searchProfileRepository;
    private final SearchPageCacheRepository searchPageCacheRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.habr.base-url}")
    private String baseUrl;

    @Value("${app.habr.search-url}")
    private String searchUrl;

    @Value("${app.habr.timeout-ms}")
    private int timeoutMs;

    @Value("${app.habr.user-agent}")
    private String userAgent;

    @Value("${app.cache.ttl-minutes:30}")
    private int cacheTtlMinutes;

    @Value("${app.prefetch.enabled:true}")
    private boolean prefetchEnabled;

    @Value("${app.prefetch.max-pages:10}")
    private int prefetchMaxPages;

    @Value("${app.prefetch.recent-request-window-minutes:1440}")
    private int prefetchRecentWindowMinutes;

    @Value("${app.prefetch.request-delay-ms:500}")
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
            log.warn("Habr Career crawl failed, returning cache fallback: {}", e.getMessage());
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
        LocalDateTime cutoff = LocalDateTime.now(HABR_ZONE).minusMinutes(prefetchRecentWindowMinutes);
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
            profile.setLastPrefetchedAt(LocalDateTime.now(HABR_ZONE));
            profile.setLastSuccessAt(LocalDateTime.now(HABR_ZONE));
            profile.setFailureCount(0);
            profile.setLastError(null);
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            profile.setLastPrefetchedAt(LocalDateTime.now(HABR_ZONE));
            profile.setFailureCount(profile.getFailureCount() + 1);
            profile.setLastError(limit(e.getMessage(), 1000));
            searchProfileRepository.save(profile);
            log.warn("Habr Career background prefetch failed for {}: {}", profile.getCacheKey(), e.getMessage());
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
        log.info("Habr Career parser request URL: {}", uri);

        Document document = Jsoup.connect(uri.toString())
                .userAgent(userAgent)
                .referrer(baseUrl)
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();

        JsonNode state = extractState(document);
        JsonNode vacanciesNode = state.path("vacancies");
        List<ScrapedVacancy> parsedItems = parseVacancies(vacanciesNode.path("list"));
        if (criteria.onlyWithSalary()) {
            parsedItems = parsedItems.stream().filter(item -> item.getSalaryFrom() != null || item.getSalaryTo() != null).toList();
        }
        if (criteria.period() != null && criteria.period() > 0) {
            LocalDateTime cutoff = LocalDateTime.now(HABR_ZONE).minusDays(criteria.period());
            parsedItems = parsedItems.stream().filter(item -> item.getPublishedAt() == null || !item.getPublishedAt().isBefore(cutoff)).toList();
        }

        List<ScrapedVacancy> persisted = upsert(parsedItems);
        JsonNode meta = vacanciesNode.path("meta");
        long found = meta.path("totalResults").asLong(persisted.size());
        int pages = meta.path("totalPages").asInt(persisted.isEmpty() ? 0 : criteria.page() + 1);
        return new SearchResult(found, pages, persisted);
    }

    private JsonNode extractState(Document document) throws IOException {
        Element stateScript = document.selectFirst("script[type=application/json][data-ssr-state=true]");
        if (stateScript == null) {
            throw new IOException("Habr Career SSR state was not found");
        }
        return objectMapper.readTree(stateScript.data());
    }

    private List<ScrapedVacancy> parseVacancies(JsonNode listNode) {
        List<ScrapedVacancy> vacancies = new ArrayList<>();
        if (!listNode.isArray()) {
            return vacancies;
        }
        LocalDateTime now = LocalDateTime.now(HABR_ZONE);
        for (JsonNode item : listNode) {
            String id = text(item.path("id"));
            String title = text(item.path("title"));
            String href = text(item.path("href"));
            if (id == null || title == null || href == null) {
                continue;
            }

            ScrapedVacancy vacancy = new ScrapedVacancy();
            vacancy.setExternalId("habr-" + id);
            vacancy.setTitle(limit(title, 512));
            vacancy.setAlternateUrl(toAbsoluteUrl(href));
            vacancy.setEmployerName(limit(text(item.path("company").path("title")), 255));
            vacancy.setAreaName(limit(extractArea(item), 255));
            vacancy.setRawPublishedText(limit(text(item.path("publishedDate").path("title")), 255));
            vacancy.setPublishedAt(parsePublishedAt(text(item.path("publishedDate").path("date")), now));
            vacancy.setSnippetRequirement(limit(extractSkills(item), 4000));
            vacancy.setSnippetResponsibility(limit(extractDivisions(item), 4000));
            vacancy.setFirstSeenAt(now);
            vacancy.setLastSeenAt(now);
            applySalary(vacancy, item.path("salary"), item.path("predictedSalary"));
            applyWorkFormat(vacancy, item);
            vacancies.add(vacancy);
        }
        return vacancies;
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

    private void registerProfile(SearchCriteria criteria) {
        try {
            SearchProfile profile = searchProfileRepository.findByCacheKey(criteria.cacheKey()).orElseGet(SearchProfile::new);
            profile.setCacheKey(criteria.cacheKey());
            profile.setParamsJson(objectMapper.writeValueAsString(criteria.paramsForStorage()));
            profile.setQueryText(criteria.text());
            profile.setAreas(String.join(",", criteria.areas()));
            profile.setEnabled(true);
            profile.setLastRequestedAt(LocalDateTime.now(HABR_ZONE));
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            log.warn("Failed to register Habr parser profile {}: {}", criteria.cacheKey(), e.getMessage());
        }
    }

    private ApiSearchResult loadFreshPageCache(SearchCriteria criteria) {
        ApiSearchResult cached = loadLatestPageCache(criteria);
        if (cached == null) {
            return null;
        }
        LocalDateTime cutoff = LocalDateTime.now(HABR_ZONE).minusMinutes(cacheTtlMinutes);
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
            log.warn("Failed to load Habr page cache {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
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
            cache.setFetchedAt(LocalDateTime.now(HABR_ZONE));
            searchPageCacheRepository.save(cache);
            searchPageCacheRepository.deleteExpiredByCacheKey(criteria.cacheKey(), LocalDateTime.now(HABR_ZONE).minus(Duration.ofDays(2)));
        } catch (Exception e) {
            log.warn("Failed to save Habr page cache {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
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
        int requestedPerPage = clamp(parseInt(params.getFirst("per_page"), 25), 1, 100);
        boolean onlyWithSalary = Boolean.parseBoolean(Optional.ofNullable(params.getFirst("only_with_salary")).orElse("false"));
        Integer period = parseNullableInt(params.getFirst("period"));
        List<String> areas = normalizeList(params.get("area"));
        List<String> schedules = normalizeList(params.get("schedule"));
        List<String> workFormats = normalizeList(params.get("work_format"));

        Map<String, List<String>> paramsForStorage = new TreeMap<>();
        putIfNotEmpty(paramsForStorage, "text", text == null ? List.of() : List.of(text));
        putIfNotEmpty(paramsForStorage, "area", areas);
        putIfNotEmpty(paramsForStorage, "schedule", schedules);
        putIfNotEmpty(paramsForStorage, "work_format", workFormats);
        putIfNotEmpty(paramsForStorage, "only_with_salary", List.of(Boolean.toString(onlyWithSalary)));
        if (period != null) {
            putIfNotEmpty(paramsForStorage, "period", List.of(Integer.toString(period)));
        }

        String cacheKey = buildCacheKey(text, areas, schedules, workFormats, onlyWithSalary, period);
        return new SearchCriteria(text, areas, page, requestedPerPage, onlyWithSalary, period, schedules, workFormats, cacheKey, paramsForStorage);
    }

    private URI buildSearchUri(SearchCriteria criteria) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(searchUrl)
                .queryParam("type", "all")
                .queryParam("page", criteria.page() + 1);
        if (criteria.text() != null) {
            builder.queryParam("q", criteria.text());
        }
        resolveCityIds(criteria.areas()).forEach(cityId -> builder.queryParam("city_id", cityId));
        if (hasRemoteFilter(criteria)) {
            builder.queryParam("remote", "true");
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private List<String> resolveCityIds(List<String> areas) {
        List<String> result = new ArrayList<>();
        for (String area : areas) {
            switch (area) {
                case "1", "2019" -> result.add("678");
                case "2" -> result.add("679");
                default -> {
                }
            }
        }
        return result.stream().distinct().toList();
    }

    private boolean hasRemoteFilter(SearchCriteria criteria) {
        String joined = String.join(",", criteria.schedules()) + "," + String.join(",", criteria.workFormats());
        String lower = joined.toLowerCase(Locale.ROOT);
        return lower.contains("remote") || lower.contains("удал");
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

    private void applySalary(ScrapedVacancy vacancy, JsonNode salary, JsonNode predictedSalary) {
        JsonNode source = salary != null && (salary.path("from").isNumber() || salary.path("to").isNumber()) ? salary : predictedSalary;
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        vacancy.setSalaryFrom(source.path("from").isNumber() ? source.path("from").asInt() : null);
        vacancy.setSalaryTo(source.path("to").isNumber() ? source.path("to").asInt() : null);
        String currency = normalizeBlank(text(source.path("currency")));
        vacancy.setSalaryCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT).replace("RUR", "RUR"));
        vacancy.setSalaryText(limit(text(source.path("formatted")), 512));
    }

    private void applyWorkFormat(ScrapedVacancy vacancy, JsonNode item) {
        boolean remote = item.path("remoteWork").asBoolean(false);
        String employment = text(item.path("employment"));
        if (remote) {
            vacancy.setWorkFormatId("REMOTE");
            vacancy.setWorkFormatName("Удалённо");
            vacancy.setScheduleName("Удалённая работа");
            return;
        }
        if (employment != null && employment.contains("part")) {
            vacancy.setScheduleName("Частичная занятость");
        } else {
            vacancy.setScheduleName("Офис");
        }
        vacancy.setWorkFormatId("ON_SITE");
        vacancy.setWorkFormatName("Офис");
    }

    private String extractArea(JsonNode item) {
        List<String> areas = new ArrayList<>();
        JsonNode locations = item.path("locations");
        if (locations.isArray()) {
            for (JsonNode location : locations) {
                String title = text(location.path("title"));
                if (title != null) {
                    areas.add(title);
                }
            }
        }
        String singleLocation = text(item.path("location").path("title"));
        if (singleLocation != null) {
            areas.add(singleLocation);
        }
        return areas.isEmpty() ? null : String.join(", ", areas.stream().distinct().toList());
    }

    private String extractSkills(JsonNode item) {
        return extractTitles(item.path("skills"));
    }

    private String extractDivisions(JsonNode item) {
        List<String> parts = new ArrayList<>();
        String qualification = text(item.path("qualification"));
        if (qualification != null) {
            parts.add(qualification);
        }
        String divisions = extractTitles(item.path("divisions"));
        if (divisions != null) {
            parts.add(divisions);
        }
        return parts.isEmpty() ? null : String.join(". ", parts);
    }

    private String extractTitles(JsonNode listNode) {
        if (!listNode.isArray()) {
            return null;
        }
        List<String> titles = new ArrayList<>();
        for (JsonNode node : listNode) {
            String title = text(node.path("title"));
            if (title != null) {
                titles.add(title);
            }
        }
        return titles.isEmpty() ? null : String.join(", ", titles);
    }

    private String toAbsoluteUrl(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        return baseUrl + href;
    }

    private LocalDateTime parsePublishedAt(String value, LocalDateTime fallback) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(HABR_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            return fallback;
        }
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
        LocalDateTime value = publishedAt != null ? publishedAt : LocalDateTime.now(HABR_ZONE);
        return value.atZone(HABR_ZONE).format(HH_DATE_FORMATTER);
    }

    private String buildCacheKey(String text, List<String> areas, List<String> schedules, List<String> workFormats, boolean onlyWithSalary, Integer period) {
        return String.join("|",
                "text=" + Optional.ofNullable(text).orElse(""),
                "areas=" + String.join(",", areas),
                "schedules=" + String.join(",", schedules),
                "workFormats=" + String.join(",", workFormats),
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

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return normalizeBlank(node.asText());
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
            List<String> schedules,
            List<String> workFormats,
            String cacheKey,
            Map<String, List<String>> paramsForStorage
    ) {
        private SearchCriteria withPage(int nextPage) {
            return new SearchCriteria(text, areas, nextPage, requestedPerPage, onlyWithSalary, period, schedules, workFormats, cacheKey, paramsForStorage);
        }
    }
}