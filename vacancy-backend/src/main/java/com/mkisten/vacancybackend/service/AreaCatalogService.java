package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.CityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AreaCatalogService {

    private final HHruAreaService hhruAreaService;
    private final RabotaByAreaService rabotaByAreaService;

    public List<CityDto> getCities(Set<String> countries) {
        if (countries == null || countries.isEmpty()) {
            return mergeAndSort(hhruAreaService.getCities(Set.of("russia")), rabotaByAreaService.getCities());
        }

        List<CityDto> result = new ArrayList<>();
        if (containsCountry(countries, "russia")) {
            result.addAll(hhruAreaService.getCities(Set.of("russia")));
        }
        if (containsCountry(countries, "belarus")) {
            result.addAll(rabotaByAreaService.getCities());
        }
        if (result.isEmpty()) {
            return mergeAndSort(hhruAreaService.getCities(countries));
        }
        return mergeAndSort(result);
    }

    private boolean containsCountry(Set<String> countries, String code) {
        for (String country : countries) {
            if (country != null && code.equals(country.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<CityDto> mergeAndSort(List<CityDto>... lists) {
        Map<String, CityDto> unique = new LinkedHashMap<>();
        for (List<CityDto> list : lists) {
            for (CityDto city : list) {
                unique.putIfAbsent(city.getId(), city);
            }
        }
        return unique.values().stream()
                .sorted(Comparator
                        .comparing(CityDto::getCountryId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CityDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
