package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.VacancyResponse;
import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class VacancyStreamService {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long telegramId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(telegramId, id -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(telegramId, emitter));
        emitter.onTimeout(() -> removeEmitter(telegramId, emitter));
        emitter.onError((e) -> removeEmitter(telegramId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("timestamp", Instant.now().toString())));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE event for user {}", telegramId, e);
        }

        return emitter;
    }

    public void emitNewVacancies(Long telegramId, List<Vacancy> vacancies) {
        if (vacancies == null || vacancies.isEmpty()) {
            return;
        }
        Set<SseEmitter> userEmitters = emitters.get(telegramId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        List<VacancyResponse> payload = vacancies.stream()
                .map(VacancyResponse::new)
                .toList();

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("vacancies")
                        .data(payload));
            } catch (IOException e) {
                log.warn("Failed to send SSE update for user {}", telegramId, e);
                removeEmitter(telegramId, emitter);
            }
        }
    }

    private void removeEmitter(Long telegramId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(telegramId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(telegramId);
            }
        }
    }
}
