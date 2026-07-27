package com.mkisten.superjobparserbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkisten.superjobparserbackend.entity.ScrapedVacancy;
import com.mkisten.superjobparserbackend.entity.SearchPageCache;
import com.mkisten.superjobparserbackend.entity.SearchProfile;
import com.mkisten.superjobparserbackend.repository.ScrapedVacancyRepository;
import com.mkisten.superjobparserbackend.repository.SearchPageCacheRepository;
import com.mkisten.superjobparserbackend.repository.SearchProfileRepository;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
public class SuperjobVacancySearchService {

    private static final String DEFAULT_BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final Pattern VACANCY_ID_PATTERN = Pattern.compile("-(\\d+)\\.html");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d[\\d\\s]*)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/Moscow");
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

    @Value("${app.superjob.base-url}")
    private String baseUrl;

    @Value("${app.superjob.search-url}")
    private String searchUrl;

    @Value("${app.superjob.belarus-search-url}")
    private String belarusSearchUrl;

    @Value("${app.superjob.timeout-ms}")
    private int timeoutMs;

    @Value("${app.superjob.user-agent}")
    private String userAgent;

    @Value("${app.superjob.page-size:40}")
    private int sourcePageSize;

    @Value("${app.cache.ttl-minutes:30}")
    private int cacheTtlMinutes;

    @Value("${app.prefetch.enabled:true}")
    private boolean prefetchEnabled;

    @Value("${app.prefetch.max-pages:10}")
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
            log.warn("SuperJob HTML crawl failed, returning cache fallback: {}", e.getMessage());
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
        LocalDateTime cutoff = LocalDateTime.now(SOURCE_ZONE).minusMinutes(prefetchRecentWindowMinutes);
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
            profile.setLastPrefetchedAt(LocalDateTime.now(SOURCE_ZONE));
            profile.setLastSuccessAt(LocalDateTime.now(SOURCE_ZONE));
            profile.setFailureCount(0);
            profile.setLastError(null);
            searchProfileRepository.save(profile);
        } catch (Exception e) {
            profile.setLastPrefetchedAt(LocalDateTime.now(SOURCE_ZONE));
            profile.setFailureCount(profile.getFailureCount() + 1);
            profile.setLastError(limit(e.getMessage(), 1000));
            searchProfileRepository.save(profile);
            log.warn("Background prefetch failed for {}: {}", profile.getCacheKey(), e.getMessage());
        }
    }

    private ApiSearchResult crawlApi(SearchCriteria criteria) throws Exception {
        SearchResult live = crawl(criteria);
        List<Map<String, Object>> items = live.items().stream().map(this::toApiItem).toList();
        return new ApiSearchResult(live.found(), live.pages(), items);
    }

    @Transactional
    protected SearchResult crawl(SearchCriteria criteria) throws Exception {
        URI uri = buildSearchUri(criteria);
        log.info("SuperJob parser request URL: {}", uri);

        Document document = Jsoup.connect(uri.toString())
                .userAgent(resolveUserAgent())
                .referrer(baseUrl)
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(timeoutMs)
                .followRedirects(true)
                .maxBodySize(0)
                .get();

        SearchPagePayload payload = parseAppState(document, criteria).orElseGet(() -> {
            List<ScrapedVacancy> cards = parseCards(document);
            Long found = parseFound(document).orElse((long) cards.size());
            Integer pages = cards.isEmpty() ? 0 : (cards.size() >= sourcePageSize ? criteria.page() + 2 : criteria.page() + 1);
            return new SearchPagePayload(cards, found, pages);
        });

        List<ScrapedVacancy> parsedItems = payload.items();
        if (criteria.onlyWithSalary()) {
            parsedItems = parsedItems.stream().filter(item -> item.getSalaryFrom() != null || item.getSalaryTo() != null).toList();
        }
        if (!criteria.workFormats().isEmpty()) {
            Set<String> allowedFormats = new LinkedHashSet<>(criteria.workFormats());
            parsedItems = parsedItems.stream()
                    .filter(item -> item.getWorkFormatId() != null && allowedFormats.contains(item.getWorkFormatId()))
                    .toList();
        }
        if (criteria.cityName() != null) {
            String normalizedCityName = normalizeForCompare(criteria.cityName());
            parsedItems = parsedItems.stream()
                    .filter(item -> normalizeForCompare(item.getAreaName()).contains(normalizedCityName))
                    .toList();
        }
        if (criteria.period() != null && criteria.period() > 0) {
            LocalDateTime cutoff = LocalDateTime.now(SOURCE_ZONE).minusDays(criteria.period());
            parsedItems = parsedItems.stream().filter(item -> item.getPublishedAt() == null || !item.getPublishedAt().isBefore(cutoff)).toList();
        }

        List<ScrapedVacancy> persisted = upsert(parsedItems);
        long found = payload.found() != null ? payload.found() : persisted.size();
        int pages = payload.pages() != null
                ? payload.pages()
                : (persisted.isEmpty() ? 0 : (persisted.size() >= sourcePageSize ? criteria.page() + 2 : criteria.page() + 1));
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
            profile.setLastRequestedAt(LocalDateTime.now(SOURCE_ZONE));
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
        LocalDateTime cutoff = LocalDateTime.now(SOURCE_ZONE).minusMinutes(cacheTtlMinutes);
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
            cache.setFetchedAt(LocalDateTime.now(SOURCE_ZONE));
            searchPageCacheRepository.save(cache);
            searchPageCacheRepository.deleteExpiredByCacheKey(criteria.cacheKey(), LocalDateTime.now(SOURCE_ZONE).minus(Duration.ofDays(2)));
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

    private SearchCriteria criteriaFromProfile(SearchProfile profile) throws Exception {
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
        String country = normalizeCountry(params.getFirst("country"));
        String cityName = normalizeBlank(params.getFirst("city_name"));
        String town = normalizeBlank(params.getFirst("town"));
        List<String> areas = normalizeList(params.get("area"));
        List<String> professionalRoles = normalizeList(params.get("professional_role"));
        List<String> schedules = normalizeList(params.get("schedule"));
        List<String> workFormats = normalizeWorkFormats(params.get("work_format"));

        Map<String, List<String>> paramsForStorage = new TreeMap<>();
        putIfNotEmpty(paramsForStorage, "text", text == null ? List.of() : List.of(text));
        putIfNotEmpty(paramsForStorage, "country", country == null ? List.of() : List.of(country));
        putIfNotEmpty(paramsForStorage, "city_name", cityName == null ? List.of() : List.of(cityName));
        putIfNotEmpty(paramsForStorage, "town", town == null ? List.of() : List.of(town));
        putIfNotEmpty(paramsForStorage, "area", areas);
        putIfNotEmpty(paramsForStorage, "professional_role", professionalRoles);
        putIfNotEmpty(paramsForStorage, "schedule", schedules);
        putIfNotEmpty(paramsForStorage, "work_format", workFormats);
        putIfNotEmpty(paramsForStorage, "search_field", List.of(searchField));
        putIfNotEmpty(paramsForStorage, "only_with_salary", List.of(Boolean.toString(onlyWithSalary)));
        if (period != null) {
            putIfNotEmpty(paramsForStorage, "period", List.of(Integer.toString(period)));
        }

        String cacheKey = buildCacheKey(text, country, cityName, town, areas, professionalRoles, schedules, workFormats, searchField, onlyWithSalary, period);
        return new SearchCriteria(text, country, cityName, town, areas, page, requestedPerPage, onlyWithSalary, period, searchField, professionalRoles, schedules, workFormats, cacheKey, paramsForStorage);
    }

    private URI buildSearchUri(SearchCriteria criteria) {
        String sourceUrl = "belarus".equals(criteria.country()) ? belarusSearchUrl : searchUrl;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(sourceUrl);
        if (criteria.text() != null) {
            builder.queryParam("keywords", criteria.text());
        }
        if (criteria.page() > 0) {
            builder.queryParam("page", criteria.page() + 1);
        }
        if (criteria.onlyWithSalary()) {
            builder.queryParam("payment_defined", 1);
        }
        if (criteria.town() != null) {
            builder.queryParam("geo[t][0]", criteria.town());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    private List<String> normalizeWorkFormats(List<String> values) {
        List<String> normalized = normalizeList(values);
        if (normalized.isEmpty()) {
            return normalized;
        }
        List<String> result = new ArrayList<>();
        for (String value : normalized) {
            switch (value.toLowerCase(Locale.ROOT)) {
                case "remote" -> result.add("REMOTE");
                case "hybrid" -> result.add("HYBRID");
                case "office" -> result.add("ON_SITE");
                default -> result.add(value);
            }
        }
        return result;
    }

    private List<ScrapedVacancy> parseCards(Document document) {
        Set<Element> cards = new LinkedHashSet<>();
        for (String selector : List.of("[class*='f-test-vacancy-item-']", ".f-test-search-result-item [class*='f-test-vacancy-item-']")) {
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

    private Optional<SearchPagePayload> parseAppState(Document document, SearchCriteria criteria) {
        try {
            JsonNode appState = extractAppState(document);
            if (appState == null) {
                return Optional.empty();
            }

            JsonNode vacancyResponses = appState.path("responses").path("lists").path("vacancy");
            if (!vacancyResponses.isObject() || vacancyResponses.isEmpty()) {
                return Optional.empty();
            }

            JsonNode selectedResponse = selectVacancyResponse(vacancyResponses, criteria);
            if (selectedResponse == null) {
                return Optional.empty();
            }

            Map<String, String> alternateUrls = extractVacancyLinks(document);
            List<ScrapedVacancy> vacancies = new ArrayList<>();
            for (JsonNode idNode : selectedResponse.path("result")) {
                String externalId = normalizeBlank(idNode.asText(null));
                if (externalId == null) {
                    continue;
                }
                ScrapedVacancy vacancy = parseAppStateVacancy(appState, externalId, alternateUrls.get(externalId));
                if (vacancy != null) {
                    vacancies.add(vacancy);
                }
            }

            long found = selectedResponse.path("meta").path("total").asLong(vacancies.size());
            int limit = Math.max(selectedResponse.path("meta").path("limit").asInt(sourcePageSize), 1);
            int pages = found == 0 ? 0 : (int) Math.ceil((double) found / limit);
            return Optional.of(new SearchPagePayload(vacancies, found, pages));
        } catch (Exception e) {
            log.warn("Failed to parse SuperJob APP_STATE, fallback to cards: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode extractAppState(Document document) throws Exception {
        for (Element script : document.select("script")) {
            String data = normalizeBlank(script.data());
            if (data == null || !data.startsWith("window.APP_STATE=")) {
                continue;
            }
            String json = data.substring("window.APP_STATE=".length()).trim();
            if (json.endsWith(";")) {
                json = json.substring(0, json.length() - 1);
            }
            return objectMapper.readTree(json);
        }
        return null;
    }

    private JsonNode selectVacancyResponse(JsonNode vacancyResponses, SearchCriteria criteria) {
        JsonNode fallback = null;
        int expectedOffset = Math.max(criteria.page(), 0) * sourcePageSize;
        var fields = vacancyResponses.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode response = entry.getValue();
            if (!response.path("result").isArray()) {
                continue;
            }
            if (fallback == null) {
                fallback = response;
            }
            if (response.path("meta").path("offset").asInt(-1) == expectedOffset) {
                return response;
            }
        }
        return fallback;
    }

    private ScrapedVacancy parseAppStateVacancy(JsonNode appState, String externalId, String alternateUrl) {
        JsonNode vacancyNode = appState.path("entities").path("vacancy").path(externalId);
        if (vacancyNode.isMissingNode()) {
            return null;
        }

        JsonNode mainInfo = relatedEntity(appState, vacancyNode, "mainInfo");
        String title = normalizeBlank(mainInfo.path("attributes").path("profession").asText(null));
        if (title == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(SOURCE_ZONE);
        JsonNode detailInfo = relatedEntity(appState, vacancyNode, "detailInfo");
        JsonNode companyInfo = relatedEntity(appState, vacancyNode, "companyInfo");
        JsonNode company = relatedEntity(appState, vacancyNode, "company");
        JsonNode town = relatedEntity(appState, vacancyNode, "town");
        JsonNode searchSnippet = relatedEntity(appState, vacancyNode, "searchSnippet");
        JsonNode workType = relatedEntity(appState, detailInfo, "workType");
        JsonNode salary = relatedEntity(appState, mainInfo, "salary");
        JsonNode currency = relatedEntity(appState, salary, "currency");

        ScrapedVacancy vacancy = new ScrapedVacancy();
        vacancy.setExternalId(externalId);
        vacancy.setTitle(limit(title, 512));
        vacancy.setAlternateUrl(limit(Optional.ofNullable(normalizeBlank(alternateUrl)).orElse(baseUrl), 1024));
        vacancy.setEmployerName(limit(firstNonBlank(
                normalizeBlank(companyInfo.path("attributes").path("name").asText(null)),
                normalizeBlank(company.path("attributes").path("title").asText(null))
        ), 255));
        vacancy.setAreaName(limit(normalizeBlank(town.path("attributes").path("name").asText(null)), 255));

        applyAppStateSalary(vacancy, salary, currency);

        String updatedAt = normalizeBlank(mainInfo.path("attributes").path("updatedAt").asText(null));
        vacancy.setRawPublishedText(limit(updatedAt, 255));
        vacancy.setPublishedAt(parseOffsetDateTime(updatedAt));

        vacancy.setScheduleName(limit(normalizeBlank(workType.path("attributes").path("defaultLabel").asText(null)), 255));
        applyAppStateSnippet(vacancy, appState, searchSnippet);
        applyAppStateWorkFormat(vacancy, appState, vacancyNode, detailInfo);
        vacancy.setFirstSeenAt(now);
        vacancy.setLastSeenAt(now);
        if (vacancy.getPublishedAt() == null) {
            vacancy.setPublishedAt(now);
        }
        return vacancy;
    }

    private JsonNode relatedEntity(JsonNode appState, JsonNode owner, String relationName) {
        if (owner == null || owner.isMissingNode()) {
            return null;
        }
        JsonNode data = owner.path("relationships").path(relationName).path("data");
        String type = normalizeBlank(data.path("type").asText(null));
        String id = normalizeBlank(data.path("id").asText(null));
        if (type == null || id == null) {
            return null;
        }
        JsonNode entity = appState.path("entities").path(type).path(id);
        return entity.isMissingNode() ? null : entity;
    }

    private void applyAppStateSalary(ScrapedVacancy vacancy, JsonNode salary, JsonNode currency) {
        if (salary == null || salary.isMissingNode()) {
            vacancy.setSalaryText("По договорённости");
            return;
        }

        int minSalary = salary.path("attributes").path("minSalary").asInt(0);
        int maxSalary = salary.path("attributes").path("maxSalary").asInt(0);
        boolean paymentAgreement = salary.path("attributes").path("paymentAgreement").asBoolean(false);
        String currencyKey = normalizeBlank(currency != null ? currency.path("attributes").path("key").asText(null) : null);
        String currencySymbol = normalizeBlank(currency != null ? currency.path("attributes").path("symbol").asText(null) : null);

        vacancy.setSalaryFrom(minSalary > 0 ? minSalary : null);
        vacancy.setSalaryTo(maxSalary > 0 ? maxSalary : null);
        vacancy.setSalaryCurrency(mapCurrencyKey(currencyKey));
        vacancy.setSalaryText(limit(formatSalaryText(vacancy.getSalaryFrom(), vacancy.getSalaryTo(), paymentAgreement, currencySymbol), 512));
    }

    private String formatSalaryText(Integer salaryFrom, Integer salaryTo, boolean paymentAgreement, String currencySymbol) {
        if (paymentAgreement || (salaryFrom == null && salaryTo == null)) {
            return "По договорённости";
        }
        String suffix = currencySymbol == null ? "" : " " + currencySymbol;
        if (salaryFrom != null && salaryTo != null) {
            return salaryFrom + " - " + salaryTo + suffix;
        }
        if (salaryFrom != null) {
            return "от " + salaryFrom + suffix;
        }
        return "до " + salaryTo + suffix;
    }

    private String mapCurrencyKey(String currencyKey) {
        if (currencyKey == null) {
            return null;
        }
        return switch (currencyKey.toLowerCase(Locale.ROOT)) {
            case "rub" -> "RUR";
            case "usd" -> "USD";
            case "eur" -> "EUR";
            case "kzt" -> "KZT";
            case "byn" -> "BYN";
            default -> currencyKey.toUpperCase(Locale.ROOT);
        };
    }

    private void applyAppStateSnippet(ScrapedVacancy vacancy, JsonNode appState, JsonNode searchSnippet) {
        if (searchSnippet == null || searchSnippet.isMissingNode()) {
            return;
        }

        String combined = normalizeBlank(searchSnippet.path("attributes").path("value").asText(null));
        String requirement = null;
        String responsibility = null;
        for (JsonNode sectionRef : searchSnippet.path("relationships").path("searchSnippetSections").path("data")) {
            JsonNode section = appState.path("entities").path(sectionRef.path("type").asText("")).path(sectionRef.path("id").asText(""));
            String sectionType = normalizeBlank(section.path("attributes").path("sectionType").asText(null));
            String text = normalizeBlank(section.path("attributes").path("text").asText(null));
            if (sectionType == null || text == null) {
                continue;
            }
            switch (sectionType) {
                case "requirements" -> requirement = text;
                case "responsibilities" -> responsibility = text;
                default -> {
                }
            }
        }
        vacancy.setSnippetRequirement(limit(requirement != null ? requirement : combined, 4000));
        vacancy.setSnippetResponsibility(limit(responsibility, 4000));
    }

    private void applyAppStateWorkFormat(ScrapedVacancy vacancy, JsonNode appState, JsonNode vacancyNode, JsonNode detailInfo) {
        for (JsonNode tagRef : vacancyNode.path("relationships").path("vacancyTags").path("data")) {
            JsonNode tag = appState.path("entities").path(tagRef.path("type").asText("")).path(tagRef.path("id").asText(""));
            String key = normalizeBlank(tag.path("attributes").path("key").asText(null));
            if (key == null) {
                continue;
            }
            switch (key) {
                case "home_format" -> {
                    vacancy.setWorkFormatId("REMOTE");
                    vacancy.setWorkFormatName("Удалённо");
                    return;
                }
                case "hybrid_format" -> {
                    vacancy.setWorkFormatId("HYBRID");
                    vacancy.setWorkFormatName("Гибрид");
                    return;
                }
                case "office_format" -> {
                    vacancy.setWorkFormatId("ON_SITE");
                    vacancy.setWorkFormatName("Офис");
                    return;
                }
                default -> {
                }
            }
        }
        if (detailInfo != null && detailInfo.path("attributes").path("isRemoteWork").asBoolean(false)) {
            vacancy.setWorkFormatId("REMOTE");
            vacancy.setWorkFormatName("Удалённо");
            return;
        }
        if (vacancy.getAreaName() != null) {
            vacancy.setWorkFormatId("ON_SITE");
            vacancy.setWorkFormatName("Офис");
        }
    }

    private Map<String, String> extractVacancyLinks(Document document) {
        Map<String, String> links = new LinkedHashMap<>();
        for (Element link : document.select("a[href*='/vakansii/'][href$='.html']")) {
            String href = normalizeBlank(link.absUrl("href"));
            if (href == null) {
                href = normalizeBlank(link.attr("href"));
            }
            if (href == null) {
                continue;
            }
            String externalId = extractVacancyId(href);
            if (externalId != null) {
                links.putIfAbsent(externalId, href);
            }
        }
        return links;
    }

    private ScrapedVacancy parseCard(Element card) {
        Element titleLink = first(card, "a[href*='/vakansii/'][href$='.html']", "a[href*='/vakansii/']");
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

        LocalDateTime now = LocalDateTime.now(SOURCE_ZONE);
        ScrapedVacancy vacancy = new ScrapedVacancy();
        vacancy.setExternalId(externalId);
        vacancy.setTitle(limit(normalizeBlank(titleLink.text()), 512));
        vacancy.setAlternateUrl(href);
        vacancy.setEmployerName(limit(firstText(card, ".f-test-text-vacancy-item-company-name", "[class*='f-test-text-vacancy-item-company-name']"), 255));
        vacancy.setAreaName(limit(extractAreaName(card), 255));

        String salaryText = normalizeBlank(firstText(card, ".f-test-text-company-item-salary", "[class*='f-test-text-company-item-salary']"));
        vacancy.setSalaryText(limit(salaryText, 512));
        applySalary(vacancy, salaryText);

        String rawPublishedText = extractPublishedText(card);
        vacancy.setRawPublishedText(limit(rawPublishedText, 255));
        vacancy.setPublishedAt(parsePublishedAt(rawPublishedText));

        vacancy.setScheduleName(limit(extractEmploymentLabel(card), 255));
        String snippet = extractSnippet(card, vacancy);
        vacancy.setSnippetRequirement(limit(snippet, 4000));
        vacancy.setSnippetResponsibility(null);

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
        for (String selector : List.of("title", "meta[property='og:title']", "meta[name='description']", "h1")) {
            for (Element element : document.select(selector)) {
                String text = "meta".equals(element.tagName()) ? element.attr("content") : element.text();
                Matcher matcher = NUMBER_PATTERN.matcher(text.replace('\u00A0', ' '));
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
        LocalDateTime value = publishedAt != null ? publishedAt : LocalDateTime.now(SOURCE_ZONE);
        return value.atZone(SOURCE_ZONE).format(API_DATE_FORMATTER);
    }

    private String buildCacheKey(String text, String country, String cityName, String town, List<String> areas, List<String> roles, List<String> schedules, List<String> workFormats,
                                 String searchField, boolean onlyWithSalary, Integer period) {
        return String.join("|",
                "text=" + Optional.ofNullable(text).orElse(""),
                "country=" + Optional.ofNullable(country).orElse(""),
                "cityName=" + Optional.ofNullable(cityName).orElse(""),
                "town=" + Optional.ofNullable(town).orElse(""),
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
            return;
        }
        if (lower.contains("гибрид")) {
            vacancy.setWorkFormatId("HYBRID");
            vacancy.setWorkFormatName("Гибрид");
            return;
        }
        if (vacancy.getAreaName() != null) {
            vacancy.setWorkFormatId("ON_SITE");
            vacancy.setWorkFormatName("Офис");
        }
    }

    private LocalDateTime parsePublishedAt(String rawPublishedText) {
        LocalDateTime now = LocalDateTime.now(SOURCE_ZONE);
        if (rawPublishedText == null) {
            return now;
        }
        String raw = rawPublishedText.trim().toLowerCase(Locale.ROOT);

        Matcher todayMatcher = Pattern.compile("сегодня\\s+в\\s+(\\d{1,2}):(\\d{2})").matcher(raw);
        if (todayMatcher.find()) {
            return now.withHour(Integer.parseInt(todayMatcher.group(1)))
                    .withMinute(Integer.parseInt(todayMatcher.group(2)))
                    .withSecond(0)
                    .withNano(0);
        }
        Matcher yesterdayMatcher = Pattern.compile("вчера\\s+в\\s+(\\d{1,2}):(\\d{2})").matcher(raw);
        if (yesterdayMatcher.find()) {
            return now.minusDays(1)
                    .withHour(Integer.parseInt(yesterdayMatcher.group(1)))
                    .withMinute(Integer.parseInt(yesterdayMatcher.group(2)))
                    .withSecond(0)
                    .withNano(0);
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
            return now.minusDays(Long.parseLong(dayMatcher.group(1))).withHour(12).withMinute(0).withSecond(0).withNano(0);
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

    private String extractPublishedText(Element card) {
        for (Element span : card.select("span")) {
            String text = normalizeBlank(span.text());
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("сегодня") || lower.contains("вчера") || lower.matches("\\d{1,2}\\s+[а-я]+.*")) {
                return text;
            }
        }
        return null;
    }

    private String extractAreaName(Element card) {
        for (Element span : card.select("span")) {
            String text = normalizeBlank(span.text());
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("занятость") || lower.contains("договор") || lower.contains("сегодня") || lower.contains("вчера")) {
                continue;
            }
            Element previous = span.previousElementSibling();
            if (previous != null && previous.html().contains("pin_fill")) {
                return text;
            }
        }
        return null;
    }

    private String extractEmploymentLabel(Element card) {
        for (Element span : card.select("span")) {
            String text = normalizeBlank(span.text());
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("занятость") || lower.contains("график") || lower.contains("вахт")) {
                return text;
            }
        }
        return null;
    }

    private String extractSnippet(Element card, ScrapedVacancy vacancy) {
        String best = null;
        for (Element span : card.select("span")) {
            String text = normalizeBlank(span.text());
            if (text == null || text.length() < 30) {
                continue;
            }
            if (Objects.equals(text, vacancy.getTitle())
                    || Objects.equals(text, vacancy.getEmployerName())
                    || Objects.equals(text, vacancy.getAreaName())
                    || Objects.equals(text, vacancy.getSalaryText())
                    || Objects.equals(text, vacancy.getRawPublishedText())
                    || Objects.equals(text, vacancy.getScheduleName())) {
                continue;
            }
            if (best == null || text.length() > best.length()) {
                best = text;
            }
        }
        return best;
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

    private LocalDateTime parseOffsetDateTime(String value) {
        try {
            return value == null ? null : OffsetDateTime.parse(value).atZoneSameInstant(SOURCE_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeBlank(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
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

    private String normalizeCountry(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "russia", "belarus" -> lower;
            default -> null;
        };
    }

    private String normalizeForCompare(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record SearchResult(long found, int pages, List<ScrapedVacancy> items) {
    }

    private record ApiSearchResult(long found, int pages, List<Map<String, Object>> items) {
    }

    private record SearchPagePayload(List<ScrapedVacancy> items, Long found, Integer pages) {
    }

    private record SearchCriteria(
            String text,
            String country,
            String cityName,
            String town,
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
            return new SearchCriteria(text, country, cityName, town, areas, nextPage, requestedPerPage, onlyWithSalary, period, searchField,
                    professionalRoles, schedules, workFormats, cacheKey, paramsForStorage);
        }
    }

    private String resolveUserAgent() {
        String configuredUserAgent = normalizeBlank(userAgent);
        if (configuredUserAgent == null || configuredUserAgent.contains("SubscriptionVacancyParser")) {
            return DEFAULT_BROWSER_USER_AGENT;
        }
        return configuredUserAgent;
    }
}
