package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.AiResumeRuntimeSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResumeClientService {

    private final AiResumeAdminSettingsService aiResumeAdminSettingsService;

    public String getConfiguredModel() {
        return aiResumeAdminSettingsService.getEffectiveSettings().getModel();
    }

    public String generateRecommendation(String prompt) {
        return generateRecommendation(prompt, aiResumeAdminSettingsService.getEffectiveSettings());
    }

    @SuppressWarnings("unchecked")
    public String generateRecommendation(String prompt, AiResumeRuntimeSettings settings) {
        if (!settings.isEnabled()) {
            throw new IllegalStateException("AI-рекомендации сейчас отключены в админке");
        }
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new IllegalStateException("AI_RESUME_API_KEY is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(settings.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, settings.getUserAgent());

        Map<String, Object> requestBody = Map.of(
                "model", settings.getModel(),
                "temperature", 0.2,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        RestTemplate restTemplate = createRestTemplate(settings.getTimeoutMs());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(normalizeBaseUrl(settings.getBaseUrl()) + "/chat/completions", entity, Map.class);
        } catch (HttpStatusCodeException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String details = (responseBody == null || responseBody.isBlank()) ? "[no body]" : responseBody;
            throw new IllegalStateException(
                    ex.getStatusCode().value() + " AI provider error: " + details,
                    ex
            );
        }
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("AI provider returned empty response");
        }

        Object choicesRaw = body.get("choices");
        if (!(choicesRaw instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("AI provider response does not contain choices");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new IllegalStateException("AI provider response choice has invalid format");
        }

        Object messageRaw = choiceMap.get("message");
        if (!(messageRaw instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("AI provider response does not contain message");
        }

        Object contentRaw = messageMap.get("content");
        String content = extractContent(contentRaw);
        if (content.isBlank()) {
            throw new IllegalStateException("AI provider returned empty recommendation");
        }
        return content.trim();
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }

    private String normalizeBaseUrl(String raw) {
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Object contentRaw) {
        if (contentRaw == null) {
            return "";
        }
        if (contentRaw instanceof String text) {
            return text;
        }
        if (contentRaw instanceof List<?> parts) {
            StringJoiner joiner = new StringJoiner("\n");
            for (Object part : parts) {
                if (part instanceof Map<?, ?> partMap) {
                    Object text = partMap.get("text");
                    if (text != null) {
                        joiner.add(Objects.toString(text, ""));
                    }
                } else if (part != null) {
                    joiner.add(part.toString());
                }
            }
            return joiner.toString();
        }
        return contentRaw.toString();
    }
}
