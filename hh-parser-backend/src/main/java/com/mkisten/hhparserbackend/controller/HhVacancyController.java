package com.mkisten.hhparserbackend.controller;

import com.mkisten.hhparserbackend.service.HhVacancySearchService;
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
public class HhVacancyController {

    private final HhVacancySearchService hhVacancySearchService;

    @GetMapping("/vacancies")
    public Map<String, Object> searchVacancies(@RequestParam MultiValueMap<String, String> params) {
        return hhVacancySearchService.search(params);
    }
}
