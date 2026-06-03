package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.AiResumeAdminSettingsRequest;
import com.mkisten.vacancybackend.dto.AiResumeAdminSettingsResponse;
import com.mkisten.vacancybackend.dto.AiResumeRuntimeSettings;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.entity.AiResumeAdminSettings;
import com.mkisten.vacancybackend.repository.AiResumeAdminSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiResumeAdminSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final AiResumeAdminSettingsRepository repository;
    private final AuthServiceClient authServiceClient;

    @Value("${app.ai-resume.base-url:https://codex.sale/v1}")
    private String defaultBaseUrl;

    @Value("${app.ai-resume.api-key:}")
    private String defaultApiKey;

    @Value("${app.ai-resume.model:gpt-5.4-mini}")
    private String defaultModel;

    @Value("${app.ai-resume.timeout-ms:90000}")
    private int defaultTimeoutMs;

    @Value("${app.ai-resume.user-agent:VacancyResumeAssistant/1.0}")
    private String defaultUserAgent;

    @Value("${app.ai-resume.max-vacancy-text-length:18000}")
    private int defaultMaxVacancyTextLength;

    @Value("${app.ai-resume.max-resume-text-length:30000}")
    private int defaultMaxResumeTextLength;

    @Value("${app.ai-resume.enabled:true}")
    private boolean defaultEnabled;

    @Transactional(readOnly = true)
    public AiResumeRuntimeSettings getEffectiveSettings() {
        return mergeWithDefaults(repository.findById(SETTINGS_ID).orElse(null));
    }

    @Transactional(readOnly = true)
    public AiResumeAdminSettingsResponse getAdminSettings(String token) {
        requireAdmin(token);
        AiResumeAdminSettings entity = repository.findById(SETTINGS_ID).orElse(null);
        return AiResumeAdminSettingsResponse.fromEntityAndRuntime(entity, mergeWithDefaults(entity));
    }

    @Transactional
    public AiResumeAdminSettingsResponse saveAdminSettings(String token, AiResumeAdminSettingsRequest request) {
        ProfileResponse profile = requireAdmin(token);
        AiResumeAdminSettings entity = repository.findById(SETTINGS_ID).orElseGet(AiResumeAdminSettings::new);
        entity.setId(SETTINGS_ID);
        entity.setEnabled(firstNonNull(request.getEnabled(), defaultEnabled));
        entity.setBaseUrl(normalizeText(request.getBaseUrl(), defaultBaseUrl));
        entity.setApiKey(normalizeText(request.getApiKey(), defaultApiKey));
        entity.setModel(normalizeText(request.getModel(), defaultModel));
        entity.setTimeoutMs(sanitizePositiveInt(request.getTimeoutMs(), defaultTimeoutMs));
        entity.setUserAgent(normalizeText(request.getUserAgent(), defaultUserAgent));
        entity.setMaxVacancyTextLength(sanitizePositiveInt(request.getMaxVacancyTextLength(), defaultMaxVacancyTextLength));
        entity.setMaxResumeTextLength(sanitizePositiveInt(request.getMaxResumeTextLength(), defaultMaxResumeTextLength));
        entity.setPromptTemplate(normalizeText(request.getPromptTemplate(), AiResumePromptService.DEFAULT_TEMPLATE));
        entity.setUpdatedByTelegramId(profile.getTelegramId());

        AiResumeAdminSettings saved = repository.save(entity);
        return AiResumeAdminSettingsResponse.fromEntityAndRuntime(saved, mergeWithDefaults(saved));
    }

    @Transactional(readOnly = true)
    public AiResumeRuntimeSettings buildRuntimeSettingsForTest(String token, AiResumeAdminSettingsRequest request) {
        requireAdmin(token);
        AiResumeAdminSettings transientEntity = new AiResumeAdminSettings();
        transientEntity.setId(SETTINGS_ID);
        transientEntity.setEnabled(firstNonNull(request.getEnabled(), defaultEnabled));
        transientEntity.setBaseUrl(normalizeText(request.getBaseUrl(), defaultBaseUrl));
        transientEntity.setApiKey(normalizeText(request.getApiKey(), defaultApiKey));
        transientEntity.setModel(normalizeText(request.getModel(), defaultModel));
        transientEntity.setTimeoutMs(sanitizePositiveInt(request.getTimeoutMs(), defaultTimeoutMs));
        transientEntity.setUserAgent(normalizeText(request.getUserAgent(), defaultUserAgent));
        transientEntity.setMaxVacancyTextLength(sanitizePositiveInt(request.getMaxVacancyTextLength(), defaultMaxVacancyTextLength));
        transientEntity.setMaxResumeTextLength(sanitizePositiveInt(request.getMaxResumeTextLength(), defaultMaxResumeTextLength));
        transientEntity.setPromptTemplate(normalizeText(request.getPromptTemplate(), AiResumePromptService.DEFAULT_TEMPLATE));
        return mergeWithDefaults(transientEntity);
    }

    private ProfileResponse requireAdmin(String token) {
        ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
        if (profile == null || !isAdminRole(profile.getRole())) {
            throw new IllegalStateException("Недостаточно прав для управления AI-настройками");
        }
        return profile;
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "MODERATOR".equals(role);
    }

    private AiResumeRuntimeSettings mergeWithDefaults(AiResumeAdminSettings entity) {
        return AiResumeRuntimeSettings.builder()
                .enabled(entity != null && entity.getEnabled() != null ? entity.getEnabled() : defaultEnabled)
                .baseUrl(entity != null ? normalizeText(entity.getBaseUrl(), defaultBaseUrl) : defaultBaseUrl)
                .apiKey(entity != null ? normalizeText(entity.getApiKey(), defaultApiKey) : defaultApiKey)
                .model(entity != null ? normalizeText(entity.getModel(), defaultModel) : defaultModel)
                .timeoutMs(entity != null ? sanitizePositiveInt(entity.getTimeoutMs(), defaultTimeoutMs) : defaultTimeoutMs)
                .userAgent(entity != null ? normalizeText(entity.getUserAgent(), defaultUserAgent) : defaultUserAgent)
                .maxVacancyTextLength(entity != null ? sanitizePositiveInt(entity.getMaxVacancyTextLength(), defaultMaxVacancyTextLength) : defaultMaxVacancyTextLength)
                .maxResumeTextLength(entity != null ? sanitizePositiveInt(entity.getMaxResumeTextLength(), defaultMaxResumeTextLength) : defaultMaxResumeTextLength)
                .promptTemplate(entity != null ? normalizeText(entity.getPromptTemplate(), AiResumePromptService.DEFAULT_TEMPLATE) : AiResumePromptService.DEFAULT_TEMPLATE)
                .build();
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private int sanitizePositiveInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean firstNonNull(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
