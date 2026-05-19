package com.mkisten.habrparserbackend.controller;

import com.mkisten.habrparserbackend.service.HabrVacancySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class HabrVacancyController {

    private final HabrVacancySearchService habrVacancySearchService;

    @GetMapping("/vacancies")
    public Map<String, Object> searchVacancies(@RequestParam MultiValueMap<String, String> params) {
        return habrVacancySearchService.search(params);
    }
}