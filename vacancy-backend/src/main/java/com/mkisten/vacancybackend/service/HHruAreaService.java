package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.CityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HHruAreaService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Map<String, String> COUNTRY_TO_ID = Map.of(
            "russia", "113",
            "belarus", "16"
    );

    private final RestTemplate restTemplate;

    @Value("${app.hhru.base-url}")
    private String baseUrl;

    private List<CityDto> cachedCities = Collections.emptyList();
    private Instant cacheUpdatedAt = Instant.EPOCH;

    public List<CityDto> getCities(Set<String> countries) {
        List<CityDto> cities = ensureCache();
        if (countries == null || countries.isEmpty()) {
            return cities;
        }
        List<String> allowedCountryIds = new ArrayList<>();
        for (String country : countries) {
            String key = country == null ? "" : country.toLowerCase(Locale.ROOT);
            String countryId = COUNTRY_TO_ID.get(key);
            if (countryId != null) {
                allowedCountryIds.add(countryId);
            }
        }
        if (allowedCountryIds.isEmpty()) {
            return cities;
        }
        List<CityDto> filtered = new ArrayList<>();
        for (CityDto city : cities) {
            if (allowedCountryIds.contains(city.getCountryId())) {
                filtered.add(city);
            }
        }
        return filtered;
    }

    private synchronized List<CityDto> ensureCache() {
        if (Duration.between(cacheUpdatedAt, Instant.now()).compareTo(CACHE_TTL) < 0 && !cachedCities.isEmpty()) {
            return cachedCities;
        }
        try {
            List<Map<String, Object>> response = restTemplate.getForObject(
                    baseUrl + "/areas", List.class
            );
            if (response == null) {
                return cachedCities;
            }
            List<CityDto> result = new ArrayList<>();
            for (Map<String, Object> country : response) {
                String countryId = String.valueOf(country.get("id"));
                Object areasRaw = country.get("areas");
                if (areasRaw instanceof List) {
                    List<Map<String, Object>> areas = (List<Map<String, Object>>) areasRaw;
                    collectLeafAreas(countryId, areas, result);
                }
            }
            cachedCities = result;
            cacheUpdatedAt = Instant.now();
            return cachedCities;
        } catch (Exception e) {
            log.error("Failed to load HH.ru areas: {}", e.getMessage(), e);
            return cachedCities;
        }
    }

    private void collectLeafAreas(String countryId, List<Map<String, Object>> areas, List<CityDto> output) {
        for (Map<String, Object> area : areas) {
            Object childrenRaw = area.get("areas");
            if (childrenRaw instanceof List) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) childrenRaw;
                if (children.isEmpty()) {
                    output.add(new CityDto(
                            String.valueOf(area.get("id")),
                            String.valueOf(area.get("name")),
                            countryId
                    ));
                } else {
                    collectLeafAreas(countryId, children, output);
                }
            }
        }
    }
}
