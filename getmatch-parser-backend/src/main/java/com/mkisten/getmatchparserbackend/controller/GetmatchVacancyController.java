package com.mkisten.getmatchparserbackend.controller;

import com.mkisten.getmatchparserbackend.service.GetmatchVacancySearchService;
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
public class GetmatchVacancyController {

    private final GetmatchVacancySearchService getmatchVacancySearchService;

    @GetMapping("/vacancies")
    public Map<String, Object> searchVacancies(@RequestParam MultiValueMap<String, String> params) {
        return getmatchVacancySearchService.search(params);
    }
}
