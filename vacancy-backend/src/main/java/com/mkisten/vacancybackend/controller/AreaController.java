package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.dto.CityDto;
import com.mkisten.vacancybackend.service.HHruAreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/areas")
@RequiredArgsConstructor
@Tag(name = "Areas", description = "Справочник городов HH.ru")
public class AreaController {

    private final HHruAreaService hhruAreaService;

    @Operation(summary = "Получить список городов")
    @GetMapping("/cities")
    public ResponseEntity<List<CityDto>> getCities(
            @RequestParam(value = "countries", required = false) Set<String> countries
    ) {
        return ResponseEntity.ok(hhruAreaService.getCities(countries));
    }
}
