package com.mkisten.hhparserbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class HhAreaController {

    private final RestTemplate restTemplate;

    @GetMapping("/areas")
    public List<Map<String, Object>> getAreas() {
        List<Map<String, Object>> response = restTemplate.getForObject(
                "https://api.hh.ru/areas",
                List.class
        );
        return response == null ? List.of() : response;
    }
}
