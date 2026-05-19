package com.mkisten.superjobparserbackend.controller;

import com.mkisten.superjobparserbackend.service.SuperjobVacancySearchService;
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
public class SuperjobVacancyController {

    private final SuperjobVacancySearchService superjobVacancySearchService;

    @GetMapping("/vacancies")
    public Map<String, Object> searchVacancies(@RequestParam MultiValueMap<String, String> params) {
        return superjobVacancySearchService.search(params);
    }
}
