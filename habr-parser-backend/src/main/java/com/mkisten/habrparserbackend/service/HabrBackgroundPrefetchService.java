package com.mkisten.habrparserbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HabrBackgroundPrefetchService {

    private final HabrVacancySearchService habrVacancySearchService;

    @Scheduled(fixedDelayString = "${app.prefetch.fixed-delay-ms:300000}", initialDelayString = "${app.prefetch.initial-delay-ms:120000}")
    public void prefetchDueProfiles() {
        habrVacancySearchService.prefetchDueProfiles();
    }
}