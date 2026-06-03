package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.CityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RabotaByAreaService {

    private static final String BELARUS_COUNTRY_ID = "16";

    private final HHruAreaService hhruAreaService;

    public List<CityDto> getCities() {
        return hhruAreaService.getCities(Set.of("belarus")).stream()
                .sorted(Comparator.comparing(CityDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public CityDto findCityById(String cityId) {
        CityDto city = hhruAreaService.findCityById(cityId);
        if (city == null || !BELARUS_COUNTRY_ID.equals(city.getCountryId())) {
            return null;
        }
        return city;
    }
}
