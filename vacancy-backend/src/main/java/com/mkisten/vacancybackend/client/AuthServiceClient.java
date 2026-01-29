package com.mkisten.vacancybackend.client;

import com.mkisten.vacancybackend.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceClient {
    @Value("${auth.service.url}")
    private String authUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Получение токена по Telegram ID
    public TokenResponse getTokenByTelegramId(Long telegramId) {
        String url = authUrl + "/api/auth/token?telegramId=" + telegramId;
        ResponseEntity<TokenResponse> resp = restTemplate.getForEntity(url, TokenResponse.class);
        return resp.getBody();
    }

    // Валидация токена
    public boolean validateToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<AuthResponse> response = restTemplate.exchange(
                    authUrl + "/api/auth/validate", HttpMethod.GET, entity, AuthResponse.class);
            return response.getBody() != null && response.getBody().isValid();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                return false;
            }
            throw ex;
        }
    }

    // Получение профиля по токену
    public ProfileResponse getCurrentUserProfile(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<ProfileResponse> resp = restTemplate.exchange(
                authUrl + "/api/auth/me", HttpMethod.GET, entity, ProfileResponse.class
        );
        return resp.getBody();
    }

    // Обновление профиля (обычно PUT)
    public ProfileResponse updateProfile(String token, ProfileResponse updateDto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProfileResponse> entity = new HttpEntity<>(updateDto, headers);
        ResponseEntity<ProfileResponse> resp = restTemplate.exchange(
                authUrl + "/api/auth/profile", HttpMethod.PUT, entity, ProfileResponse.class
        );
        return resp.getBody();
    }

    // Telegram auth: создать сессию
    public SessionStatusResponse createSession(CreateSessionRequest req) {
        ResponseEntity<SessionStatusResponse> resp = restTemplate.postForEntity(
                authUrl + "/api/telegram-auth/create-session", req, SessionStatusResponse.class
        );
        return resp.getBody();
    }

    // Telegram auth: проверить статус
    public SessionStatusResponse getSessionStatus(String sessionId, String deviceId) {
        String url = authUrl + "/api/telegram-auth/status/" + sessionId;
        if (deviceId != null && !deviceId.isBlank()) {
            url += "?deviceId=" + deviceId;
        }
        ResponseEntity<SessionStatusResponse> resp = restTemplate.getForEntity(url, SessionStatusResponse.class);
        return resp.getBody();
    }

    // Получить статус подписки
    public SubscriptionStatusResponse getSubscriptionStatus(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<SubscriptionStatusResponse> resp = restTemplate.exchange(
                authUrl + "/api/subscription/status", HttpMethod.GET, entity, SubscriptionStatusResponse.class
        );
        return resp.getBody();
    }

    public void sendTelegramNotification(String token, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("message", message);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(
                authUrl + "/api/bot/notify", request, Void.class);
    }

    public TokenResponse refreshToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<TokenResponse> response = restTemplate.exchange(
                authUrl + "/api/auth/refresh", // Укажите сюда ваш endpoint refresh на сервисе авторизации!
                HttpMethod.POST, entity, TokenResponse.class
        );
        return response.getBody();
    }
}
