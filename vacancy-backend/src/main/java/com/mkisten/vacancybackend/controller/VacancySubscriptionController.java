package com.mkisten.vacancybackend.controller;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class VacancySubscriptionController {
    private final AuthServiceClient authServiceClient;

    /** Получить статус подписки по токену */
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.ok(authServiceClient.getSubscriptionStatus(token));
    }
}
