package com.mkisten.vacancybackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkisten.vacancybackend.dto.SaveVacancyPresetRequest;
import com.mkisten.vacancybackend.dto.VacancyPresetDto;
import com.mkisten.vacancybackend.dto.VacancyPresetSettingsDto;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyPresetService {

    private static final int MAX_PRESETS = 12;
    private static final TypeReference<List<VacancyPresetDto>> PRESET_LIST_TYPE = new TypeReference<>() {};

    private final UserSettingsRepository userSettingsRepository;
    private final UserSettingsService userSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<VacancyPresetDto> getPresets(String token) {
        UserSettings settings = userSettingsService.getSettings(token);
        return readPresets(settings);
    }

    @Transactional
    public VacancyPresetDto savePreset(String token, SaveVacancyPresetRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("Preset name is required");
        }
        if (request.getSettings() == null) {
            throw new IllegalArgumentException("Preset settings are required");
        }

        Long telegramId = userSettingsService.getTelegramId(token);
        UserSettings userSettings = userSettingsRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userSettingsService.getSettings(token));

        List<VacancyPresetDto> presets = new ArrayList<>(readPresets(userSettings));
        VacancyPresetDto preset = new VacancyPresetDto();
        preset.setId(UUID.randomUUID().toString());
        preset.setName(request.getName().trim());
        preset.setSettings(sanitizeSettings(request.getSettings()));
        preset.setCreatedAt(LocalDateTime.now().toString());

        presets.removeIf(item -> item.getId() != null && item.getId().equals(preset.getId()));
        presets.add(0, preset);
        if (presets.size() > MAX_PRESETS) {
            presets = new ArrayList<>(presets.subList(0, MAX_PRESETS));
        }

        writePresets(userSettings, presets);
        userSettingsRepository.save(userSettings);
        return preset;
    }

    @Transactional
    public VacancyPresetDto updatePreset(String token, String presetId, SaveVacancyPresetRequest request) {
        if (!StringUtils.hasText(presetId)) {
            throw new IllegalArgumentException("Preset id is required");
        }
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("Preset name is required");
        }
        if (request.getSettings() == null) {
            throw new IllegalArgumentException("Preset settings are required");
        }

        Long telegramId = userSettingsService.getTelegramId(token);
        UserSettings userSettings = userSettingsRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userSettingsService.getSettings(token));

        List<VacancyPresetDto> presets = new ArrayList<>(readPresets(userSettings));
        VacancyPresetDto existingPreset = presets.stream()
                .filter(item -> presetId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Preset not found"));

        existingPreset.setName(request.getName().trim());
        existingPreset.setSettings(sanitizeSettings(request.getSettings()));

        presets.removeIf(item -> presetId.equals(item.getId()));
        presets.add(0, existingPreset);

        writePresets(userSettings, presets);
        userSettingsRepository.save(userSettings);
        return existingPreset;
    }

    @Transactional
    public void deletePreset(String token, String presetId) {
        if (!StringUtils.hasText(presetId)) {
            return;
        }

        Long telegramId = userSettingsService.getTelegramId(token);
        UserSettings userSettings = userSettingsRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userSettingsService.getSettings(token));

        List<VacancyPresetDto> presets = new ArrayList<>(readPresets(userSettings));
        boolean removed = presets.removeIf(item -> presetId.equals(item.getId()));
        if (!removed) {
            return;
        }

        writePresets(userSettings, presets);
        userSettingsRepository.save(userSettings);
    }

    private List<VacancyPresetDto> readPresets(UserSettings settings) {
        if (settings == null || !StringUtils.hasText(settings.getSearchPresetsJson())) {
            return Collections.emptyList();
        }

        try {
            List<VacancyPresetDto> presets = objectMapper.readValue(settings.getSearchPresetsJson(), PRESET_LIST_TYPE);
            return presets == null ? Collections.emptyList() : presets;
        } catch (Exception e) {
            log.warn("Failed to parse stored vacancy presets for {}: {}", settings.getTelegramId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private void writePresets(UserSettings settings, List<VacancyPresetDto> presets) {
        try {
            settings.setSearchPresetsJson(objectMapper.writeValueAsString(presets));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save vacancy presets", e);
        }
    }

    private VacancyPresetSettingsDto sanitizeSettings(VacancyPresetSettingsDto settings) {
        VacancyPresetSettingsDto sanitized = new VacancyPresetSettingsDto();
        sanitized.setSearchQuery(defaultString(settings.getSearchQuery()));
        sanitized.setDays(settings.getDays() == null ? 1 : settings.getDays());
        sanitized.setExcludeKeywords(defaultString(settings.getExcludeKeywords()));
        sanitized.setExcludeCompanies(defaultString(settings.getExcludeCompanies()));
        sanitized.setCityId(defaultString(settings.getCityId()));
        sanitized.setWorkTypes(settings.getWorkTypes() == null ? new java.util.LinkedHashSet<>() : settings.getWorkTypes());
        sanitized.setCountries(settings.getCountries() == null ? new java.util.LinkedHashSet<>() : settings.getCountries());
        sanitized.setTelegramNotify(Boolean.TRUE.equals(settings.getTelegramNotify()));
        sanitized.setAutoUpdateEnabled(Boolean.TRUE.equals(settings.getAutoUpdateEnabled()));
        sanitized.setAutoUpdateInterval(settings.getAutoUpdateInterval() == null ? 30 : settings.getAutoUpdateInterval());
        sanitized.setTheme(defaultTheme(settings.getTheme()));
        return sanitized;
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultTheme(String value) {
        if (!StringUtils.hasText(value)) {
            return "light";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
