package com.mkisten.getmatchparserbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkisten.getmatchparserbackend.entity.SearchPageCache;
import com.mkisten.getmatchparserbackend.entity.SearchProfile;
import com.mkisten.getmatchparserbackend.repository.SearchPageCacheRepository;
import com.mkisten.getmatchparserbackend.repository.SearchProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetmatchVacancySearchService {

    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final TypeReference<List<Map<String, Object>>> ITEM_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> PARAMS_TYPE = new TypeReference<>() {};

    private final SearchProfileRepository searchProfileRepository;
    private final SearchPageCacheRepository searchPageCacheRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.getmatch.public-base-url}")
    private String publicBaseUrl;

    @Value("${app.getmatch.api-base-url}")
    private String apiBaseUrl;

    @Value("${app.getmatch.timeout-ms}")
    private int timeoutMs;

    @Value("${app.getmatch.user-agent}")
    private String userAgent;

    @Value("${app.getmatch.max-page-size:100}")
    private int maxPageSize;

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

        ApiSearchResult fresh = loadFreshPageCache(criteria);
        if (fresh != null) {
            return toResponse(fresh);
        }

        try {
            ApiSearchResult live = crawlApi(criteria);
            savePageCache(criteria, live);
            return toResponse(live);
        } catch (Exception e) {
            log.warn("GetMatch crawl failed for {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
            ApiSearchResult stale = loadLatestPageCache(criteria);
            if (stale != null) {
                return toResponse(stale);
            }
            return toResponse(ApiSearchResult.empty(criteria.page()));
        }
    }

    public void prefetchDueProfiles() {
        if (!prefetchEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now(MOSCOW_ZONE).minusMinutes(prefetchRecentWindowMinutes);
        List<SearchProfile> profiles = searchProfileRepository.findByEnabledTrueAndLastRequestedAtAfterOrderByLastRequestedAtDesc(cutoff);
        for (SearchProfile profile : profiles) {
            prefetchProfile(profile);
        }
    }

    @Transactional
    protected void prefetchProfile(SearchProfile profile) {
        try {
            SearchCriteria baseCriteria = criteriaFromProfile(profile);
            int discoveredPages = Math.max(1, prefetchMaxPages);
            for (int page = 0; page < Math.min(prefetchMaxPages, discoveredPages); page++) {
                SearchCriteria pageCriteria = baseCriteria.withPage(page);
                ApiSearchResult result = crawlApi(pageCriteria);
                savePageCache(pageCriteria, result);
                discoveredPages = Math.min(prefetchMaxPages, Math.max(result.pages(), page + 1));
                if (result.items().isEmpty()) {
                    break;
                }
                sleepQuietly(prefetchRequestDelayMs);
            }
            profile.setLastPrefetchedAt(LocalDateTime.now(MOSCOW_ZONE));
            profile.setLastSuccessAt(LocalDateTime.now(MOSCOW_ZONE));
            profile.setFailureCount(0);
            profile.setLastError(null);
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            profile.setLastPrefetchedAt(LocalDateTime.now(MOSCOW_ZONE));
            profile.setFailureCount(profile.getFailureCount() + 1);
            profile.setLastError(limit(e.getMessage(), 1000));
            searchProfileRepository.save(profile);
            log.warn("GetMatch background prefetch failed for {}: {}", profile.getCacheKey(), e.getMessage());
        }
    }

    private ApiSearchResult crawlApi(SearchCriteria criteria) throws IOException, InterruptedException {
        URI uri = buildSearchUri(criteria);
        log.info("GetMatch parser request URL: {}", uri);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GetMatch returned status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(stripBom(response.body()));
        JsonNode meta = root.path("meta");
        JsonNode offers = root.path("offers");

        long sourceTotal = meta.path("total").asLong(0);
        int limit = meta.path("limit").asInt(criteria.requestedPerPage());
        int sourcePages = limit <= 0 ? 0 : (int) Math.ceil((double) sourceTotal / limit);

        List<Map<String, Object>> items = new ArrayList<>();
        if (offers.isArray()) {
            for (JsonNode offer : offers) {
                if (!matchesCriteria(offer, criteria)) {
                    continue;
                }
                items.add(toApiItem(offer));
            }
        }

        return new ApiSearchResult(sourceTotal, sourcePages, items);
    }

    private boolean matchesCriteria(JsonNode offer, SearchCriteria criteria) {
        if (!matchesTitle(offer.path("position").asText(null), criteria.text())) {
            return false;
        }
        if (criteria.onlyWithSalary() && offer.path("salary_hidden").asBoolean(false)) {
            return false;
        }
        if (criteria.period() != null) {
            LocalDateTime publishedAt = parsePublishedAt(offer.path("published_at").asText(null));
            if (publishedAt != null) {
                LocalDateTime cutoff = LocalDateTime.now(MOSCOW_ZONE).minusDays(criteria.period());
                if (publishedAt.isBefore(cutoff)) {
                    return false;
                }
            }
        }
        if (!matchesAreas(offer, criteria.areas())) {
            return false;
        }
        return matchesWorkFormats(offer, criteria.workFormats());
    }

    private boolean matchesTitle(String title, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (title == null) {
            return false;
        }
        return title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private boolean matchesAreas(JsonNode offer, List<String> areas) {
        if (areas.isEmpty()) {
            return true;
        }
        Set<String> allowedCountries = new LinkedHashSet<>();
        Set<String> allowedCities = new LinkedHashSet<>();
        for (String area : areas) {
            switch (area) {
                case "113" -> allowedCountries.add("россия");
                case "16" -> allowedCountries.add("беларусь");
                case "1", "2019" -> allowedCities.add("москва");
                case "2" -> allowedCities.add("санкт-петербург");
                default -> {
                }
            }
        }
        if (allowedCountries.isEmpty() && allowedCities.isEmpty()) {
            return true;
        }

        for (JsonNode location : iterable(offer.path("location_requirements"))) {
            String country = location.path("country").asText("").toLowerCase(Locale.ROOT);
            String city = location.path("city").asText("").toLowerCase(Locale.ROOT);
            if (allowedCountries.contains(country) || allowedCities.contains(city)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesWorkFormats(JsonNode offer, List<String> workFormats) {
        if (workFormats.isEmpty()) {
            return true;
        }
        Set<String> normalizedRequested = new LinkedHashSet<>();
        for (String workFormat : workFormats) {
            String lower = workFormat.toLowerCase(Locale.ROOT);
            if (lower.contains("remote") || lower.contains("удал")) {
                normalizedRequested.add("remote");
            } else if (lower.contains("hybrid") || lower.contains("гибрид")) {
                normalizedRequested.add("hybrid");
            } else if (lower.contains("office") || lower.contains("офис") || lower.contains("onsite") || lower.contains("on_site")) {
                normalizedRequested.add("office");
            }
        }
        if (normalizedRequested.isEmpty()) {
            return true;
        }

        for (JsonNode location : iterable(offer.path("location_requirements"))) {
            String format = location.path("format").asText("").toLowerCase(Locale.ROOT);
            if (normalizedRequested.contains(format)) {
                return true;
            }
            if ("on_site".equals(format) && normalizedRequested.contains("office")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toApiItem(JsonNode offer) {
        Map<String, Object> item = new LinkedHashMap<>();
        String externalId = "getmatch-" + offer.path("id").asText();
        item.put("id", externalId);
        item.put("name", offer.path("position").asText(null));
        item.put("alternate_url", toAbsoluteUrl(offer.path("url").asText(null)));
        item.put("published_at", formatPublishedAt(parsePublishedAt(offer.path("published_at").asText(null))));

        Map<String, Object> employer = new LinkedHashMap<>();
        employer.put("name", offer.path("company").path("name").asText(null));
        item.put("employer", employer);

        Map<String, Object> area = new LinkedHashMap<>();
        area.put("name", limit(extractAreaName(offer), 100));
        item.put("area", area);

        item.put("schedule", namedMap(extractScheduleName(offer)));
        item.put("work_format", buildWorkFormat(offer));
        item.put("salary", buildSalary(offer));

        Map<String, Object> snippet = new LinkedHashMap<>();
        snippet.put("requirement", limit(joinArray(offer.path("stack")), 2000));
        snippet.put("responsibility", limit(stripHtml(offer.path("offer_description").asText(null)), 4000));
        item.put("snippet", snippet);

        return item;
    }

    private Map<String, Object> buildSalary(JsonNode offer) {
        Integer from = offer.path("salary_display_from").isNumber() ? offer.path("salary_display_from").asInt() : null;
        Integer to = offer.path("salary_display_to").isNumber() ? offer.path("salary_display_to").asInt() : null;
        String currency = normalizeBlank(offer.path("salary_currency").asText(null));
        if (from == null && to == null && currency == null) {
            return null;
        }
        Map<String, Object> salary = new LinkedHashMap<>();
        salary.put("from", from);
        salary.put("to", to);
        salary.put("currency", currency);
        return salary;
    }

    private List<Map<String, Object>> buildWorkFormat(JsonNode offer) {
        Set<String> formats = new LinkedHashSet<>();
        for (JsonNode location : iterable(offer.path("location_requirements"))) {
            String format = location.path("format").asText("").toLowerCase(Locale.ROOT);
            if (format.isBlank()) {
                continue;
            }
            formats.add(format);
        }
        if (formats.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String format : formats) {
            Map<String, Object> item = new LinkedHashMap<>();
            switch (format) {
                case "remote" -> {
                    item.put("id", "REMOTE");
                    item.put("name", "Удалённо");
                }
                case "hybrid" -> {
                    item.put("id", "HYBRID");
                    item.put("name", "Гибрид");
                }
                default -> {
                    item.put("id", "ON_SITE");
                    item.put("name", "Офис");
                }
            }
            result.add(item);
        }
        return result;
    }

    private String extractScheduleName(JsonNode offer) {
        for (JsonNode location : iterable(offer.path("location_requirements"))) {
            String format = location.path("format").asText("").toLowerCase(Locale.ROOT);
            switch (format) {
                case "remote":
                    return "Удалённо";
                case "hybrid":
                    return "Гибрид";
                case "office", "on_site":
                    return "Офис";
                default:
                    break;
            }
        }
        return null;
    }

    private String extractAreaName(JsonNode offer) {
        List<String> parts = new ArrayList<>();
        for (JsonNode item : iterable(offer.path("location_items"))) {
            String label = normalizeBlank(item.path("label").asText(null));
            if (label != null) {
                parts.add(label);
            }
        }
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
        for (JsonNode item : iterable(offer.path("location_requirements"))) {
            String city = normalizeBlank(item.path("city").asText(null));
            if (city != null) {
                parts.add(city);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void registerProfile(SearchCriteria criteria) {
        try {
            SearchProfile profile = searchProfileRepository.findByCacheKey(criteria.cacheKey()).orElseGet(SearchProfile::new);
            profile.setCacheKey(criteria.cacheKey());
            profile.setParamsJson(objectMapper.writeValueAsString(criteria.paramsForStorage()));
            profile.setQueryText(criteria.text());
            profile.setAreas(String.join(",", criteria.areas()));
            profile.setEnabled(true);
            profile.setLastRequestedAt(LocalDateTime.now(MOSCOW_ZONE));
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            log.warn("Failed to register GetMatch profile {}: {}", criteria.cacheKey(), e.getMessage());
        }
    }

    private ApiSearchResult loadFreshPageCache(SearchCriteria criteria) {
        Optional<SearchPageCache> cache = searchPageCacheRepository.findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(criteria.cacheKey(), criteria.page());
        if (cache.isEmpty()) {
            return null;
        }
        LocalDateTime cutoff = LocalDateTime.now(MOSCOW_ZONE).minusMinutes(cacheTtlMinutes);
        if (cache.get().getFetchedAt().isBefore(cutoff)) {
            return null;
        }
        return deserializeCache(cache.get(), criteria);
    }

    private ApiSearchResult loadLatestPageCache(SearchCriteria criteria) {
        return searchPageCacheRepository.findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(criteria.cacheKey(), criteria.page())
                .map(cache -> deserializeCache(cache, criteria))
                .orElse(null);
    }

    private ApiSearchResult deserializeCache(SearchPageCache cache, SearchCriteria criteria) {
        try {
            List<Map<String, Object>> items = objectMapper.readValue(cache.getItemsJson(), ITEM_LIST_TYPE);
            return new ApiSearchResult(cache.getFoundCount(), cache.getPagesCount(), items);
        } catch (Exception e) {
            log.warn("Failed to load GetMatch page cache {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
            return null;
        }
    }

    @Transactional
    protected void savePageCache(SearchCriteria criteria, ApiSearchResult result) {
        try {
            SearchPageCache cache = searchPageCacheRepository.findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(criteria.cacheKey(), criteria.page())
                    .orElseGet(SearchPageCache::new);
            cache.setCacheKey(criteria.cacheKey());
            cache.setPageNumber(criteria.page());
            cache.setFoundCount(result.found());
            cache.setPagesCount(result.pages());
            cache.setItemsJson(objectMapper.writeValueAsString(result.items()));
            cache.setItemCount(result.items().size());
            cache.setFetchedAt(LocalDateTime.now(MOSCOW_ZONE));
            searchPageCacheRepository.save(cache);
            searchPageCacheRepository.deleteExpiredByCacheKey(criteria.cacheKey(), LocalDateTime.now(MOSCOW_ZONE).minusDays(2));
        } catch (Exception e) {
            log.warn("Failed to save GetMatch page cache {} page {}: {}", criteria.cacheKey(), criteria.page(), e.getMessage());
        }
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
        int requestedPerPage = clamp(parseInt(params.getFirst("per_page"), 20), 1, maxPageSize);
        boolean onlyWithSalary = Boolean.parseBoolean(Optional.ofNullable(params.getFirst("only_with_salary")).orElse("false"));
        Integer period = parseNullableInt(params.getFirst("period"));
        List<String> areas = normalizeList(params.get("area"));
        List<String> workFormats = normalizeList(params.get("work_format"));
        String specialization = resolveSpecialization(text);

        Map<String, List<String>> paramsForStorage = new TreeMap<>();
        putIfNotEmpty(paramsForStorage, "text", text == null ? List.of() : List.of(text));
        putIfNotEmpty(paramsForStorage, "area", areas);
        putIfNotEmpty(paramsForStorage, "work_format", workFormats);
        putIfNotEmpty(paramsForStorage, "only_with_salary", List.of(Boolean.toString(onlyWithSalary)));
        if (period != null) {
            putIfNotEmpty(paramsForStorage, "period", List.of(Integer.toString(period)));
        }
        if (specialization != null) {
            putIfNotEmpty(paramsForStorage, "sp", List.of(specialization));
        }

        String cacheKey = buildCacheKey(text, areas, workFormats, onlyWithSalary, period, specialization);
        return new SearchCriteria(text, areas, page, requestedPerPage, onlyWithSalary, period, workFormats, specialization, cacheKey, paramsForStorage);
    }

    private URI buildSearchUri(SearchCriteria criteria) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/offers")
                .queryParam("limit", criteria.requestedPerPage())
                .queryParam("page", criteria.page() + 1);

        if (criteria.specialization() != null) {
            builder.queryParam("sp", criteria.specialization());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private String resolveSpecialization(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("java")) return "java_scala";
        if (lower.contains("scala")) return "java_scala";
        if (lower.contains("python")) return "python";
        if (lower.contains("golang") || lower.contains("go ")) return "golang";
        if (lower.equals("go")) return "golang";
        if (lower.contains("frontend") || lower.contains("react") || lower.contains("vue") || lower.contains("angular")) return "js_frontend";
        if (lower.contains("node")) return "js_backend";
        if (lower.contains("php")) return "php";
        if (lower.contains("ruby")) return "ruby";
        if (lower.contains("android")) return "android";
        if (lower.contains("ios")) return "ios";
        if (lower.contains("kotlin")) return "kotlin";
        if (lower.contains("c#") || lower.contains("csharp")) return "c_sharp";
        if (lower.contains("c++")) return "c_cpp";
        if (lower.contains("devops")) return "dev_ops";
        if (lower.contains("sre")) return "sre";
        if (lower.contains("product")) return "product_management";
        if (lower.contains("analyst")) return "system_analyst";
        if (lower.contains("qa")) return "qa_auto";
        return null;
    }

    private String buildCacheKey(String text,
                                 List<String> areas,
                                 List<String> workFormats,
                                 boolean onlyWithSalary,
                                 Integer period,
                                 String specialization) {
        List<String> parts = new ArrayList<>();
        parts.add("text=" + safe(text));
        parts.add("areas=" + String.join(",", areas));
        parts.add("workFormat=" + String.join(",", workFormats));
        parts.add("salary=" + onlyWithSalary);
        parts.add("period=" + (period == null ? "" : period));
        parts.add("sp=" + safe(specialization));
        return String.join("|", parts);
    }

    private Map<String, Object> toResponse(ApiSearchResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", result.found());
        response.put("pages", result.pages());
        response.put("items", result.items());
        return response;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node::elements : List.<JsonNode>of();
    }

    private String joinArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = normalizeBlank(item.asText(null));
            if (text != null) {
                values.add(text);
            }
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return normalizeBlank(html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " "));
    }

    private LocalDateTime parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatPublishedAt(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return null;
        }
        return publishedAt.atZone(MOSCOW_ZONE).format(API_DATE_FORMATTER);
    }

    private Map<String, Object> namedMap(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        return result;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private void putIfNotEmpty(Map<String, List<String>> target, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, new ArrayList<>(values));
        }
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Integer parseNullableInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String toAbsoluteUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return publicBaseUrl + path;
    }

    private String stripBom(String body) {
        return body != null && !body.isEmpty() && body.charAt(0) == '\uFEFF' ? body.substring(1) : body;
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record SearchCriteria(
            String text,
            List<String> areas,
            int page,
            int requestedPerPage,
            boolean onlyWithSalary,
            Integer period,
            List<String> workFormats,
            String specialization,
            String cacheKey,
            Map<String, List<String>> paramsForStorage
    ) {
        SearchCriteria withPage(int nextPage) {
            return new SearchCriteria(text, areas, nextPage, requestedPerPage, onlyWithSalary, period, workFormats, specialization, cacheKey, paramsForStorage);
        }
    }

    private record ApiSearchResult(long found, int pages, List<Map<String, Object>> items) {
        static ApiSearchResult empty(int page) {
            return new ApiSearchResult(0, page + 1, List.of());
        }
    }
}
