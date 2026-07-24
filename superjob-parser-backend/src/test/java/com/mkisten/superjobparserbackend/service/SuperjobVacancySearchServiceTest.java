package com.mkisten.superjobparserbackend.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuperjobVacancySearchServiceTest {

    @Test
    void candidateUrlsKeepsPrimaryRussiaUrl() throws Exception {
        SuperjobVacancySearchService service = service();

        List<String> candidates = invokeCandidateUrls(service, new URI("https://russia.superjob.ru/vacancy/search/?keywords=java"));

        assertEquals(List.of("https://russia.superjob.ru/vacancy/search/?keywords=java"), candidates);
    }

    @Test
    void candidateUrlsRewritesBelarusHostToDefaultSearchUrl() throws Exception {
        SuperjobVacancySearchService service = service();

        List<String> candidates = invokeCandidateUrls(service, new URI("https://www.superjob.by/vacancy/search/?keywords=java&geo%5Bt%5D%5B0%5D=430&page=2"));

        assertEquals(List.of("https://russia.superjob.ru/vacancy/search/?keywords=java&page=2"), candidates);
    }

    @Test
    void buildSearchUriUsesDefaultSearchHostForBelarusCountry() {
        SuperjobVacancySearchService service = service();
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("text", "java");
        params.add("country", "belarus");
        params.add("town", "430");

        URI uri = invokeBuildSearchUri(service, invokeNormalizeCriteria(service, params));

        assertEquals("https://russia.superjob.ru/vacancy/search/?keywords=java&geo%5Bt%5D%5B0%5D=430", uri.toString());
    }

    @Test
    void parseFoundReadsResultCountFromInlineHtml() {
        SuperjobVacancySearchService service = service();
        Document document = Jsoup.parse("<html><head><title>Найдено 123 вакансии</title></head><body></body></html>");

        Long found = invokeParseFound(service, document).orElseThrow();

        assertEquals(123L, found);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeCandidateUrls(SuperjobVacancySearchService service, URI uri) {
        return (List<String>) ReflectionTestUtils.invokeMethod(service, "candidateUrls", uri);
    }

    private Object invokeNormalizeCriteria(SuperjobVacancySearchService service, LinkedMultiValueMap<String, String> params) {
        return ReflectionTestUtils.invokeMethod(service, "normalizeCriteria", params);
    }

    private URI invokeBuildSearchUri(SuperjobVacancySearchService service, Object criteria) {
        return (URI) ReflectionTestUtils.invokeMethod(service, "buildSearchUri", criteria);
    }

    private java.util.Optional<Long> invokeParseFound(SuperjobVacancySearchService service, Document document) {
        return (java.util.Optional<Long>) ReflectionTestUtils.invokeMethod(service, "parseFound", document);
    }

    private SuperjobVacancySearchService service() {
        SuperjobVacancySearchService service = new SuperjobVacancySearchService(null, null, null, null);
        ReflectionTestUtils.setField(service, "baseUrl", "https://russia.superjob.ru");
        ReflectionTestUtils.setField(service, "searchUrl", "https://russia.superjob.ru/vacancy/search/");
        ReflectionTestUtils.setField(service, "timeoutMs", 15000);
        ReflectionTestUtils.setField(service, "userAgent", "Mozilla/5.0 test");
        ReflectionTestUtils.setField(service, "sourcePageSize", 40);
        ReflectionTestUtils.setField(service, "cacheTtlMinutes", 30);
        ReflectionTestUtils.setField(service, "prefetchEnabled", true);
        ReflectionTestUtils.setField(service, "prefetchMaxPages", 10);
        ReflectionTestUtils.setField(service, "prefetchRecentWindowMinutes", 1440);
        ReflectionTestUtils.setField(service, "prefetchRequestDelayMs", 250L);
        assertTrue(service != null);
        return service;
    }
}
