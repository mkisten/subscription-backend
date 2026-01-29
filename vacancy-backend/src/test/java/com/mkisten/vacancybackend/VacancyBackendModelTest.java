package com.mkisten.vacancybackend;

import com.mkisten.vacancybackend.api.error.ApiErrorResponse;
import com.mkisten.vacancybackend.api.error.SubscriptionRequiredException;
import com.mkisten.vacancybackend.dto.*;
import com.mkisten.vacancybackend.entity.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VacancyBackendModelTest {

    @Test
    void vacancyKeyEquality() {
        VacancyKey key1 = new VacancyKey("1", 10L);
        VacancyKey key2 = new VacancyKey("1", 10L);
        VacancyKey key3 = new VacancyKey("2", 10L);

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void vacancyResponseMapsFromEntity() {
        Vacancy vacancy = new Vacancy();
        vacancy.setId("123");
        vacancy.setTitle("Engineer");
        vacancy.setEmployer("Acme");
        vacancy.setCity("City");
        vacancy.setSchedule("remote");
        vacancy.setSalary("100");
        vacancy.setUrl("http://example");
        vacancy.setStatus(VacancyStatus.NEW);
        vacancy.setPublishedAt(LocalDateTime.now());
        vacancy.setLoadedAt(LocalDateTime.now());

        VacancyResponse response = new VacancyResponse(vacancy);
        assertEquals("123", response.getId());
        assertEquals("Engineer", response.getTitle());
        assertEquals(VacancyStatus.NEW, response.getStatus());
    }

    @Test
    void apiErrorResponseBuilderSetsFields() {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(403)
                .error("FORBIDDEN")
                .code("SUBSCRIPTION_REQUIRED")
                .message("required")
                .path("/api/test")
                .build();

        assertEquals(403, response.getStatus());
        assertEquals("SUBSCRIPTION_REQUIRED", response.getCode());
    }

    @Test
    void subscriptionRequiredExceptionStoresPath() {
        SubscriptionRequiredException ex = new SubscriptionRequiredException("msg", "/path");
        assertEquals("msg", ex.getMessage());
        assertEquals("/path", ex.getPath());
    }

    @Test
    void dtoAndEntityAccessors() {
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken("token");
        assertEquals("token", tokenResponse.getToken());

        SubscriptionStatusResponse statusResponse = new SubscriptionStatusResponse();
        statusResponse.setTelegramId(1L);
        statusResponse.setActive(true);
        statusResponse.setSubscriptionPlan("TRIAL");
        assertEquals(1L, statusResponse.getTelegramId());
        assertTrue(statusResponse.getActive());

        SessionStatusResponse sessionStatusResponse = new SessionStatusResponse();
        sessionStatusResponse.setSessionId("s");
        assertEquals("s", sessionStatusResponse.getSessionId());

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery("java");
        searchRequest.setDays(3);
        searchRequest.setWorkTypes(Set.of("remote"));
        assertEquals("java", searchRequest.getQuery());
        assertEquals(3, searchRequest.getDays());

        ProfileResponse profileResponse = new ProfileResponse();
        profileResponse.setTelegramId(1L);
        profileResponse.setSubscriptionEndDate(LocalDate.now());
        assertEquals(1L, profileResponse.getTelegramId());

        CreateSessionRequest createSessionRequest = new CreateSessionRequest();
        createSessionRequest.setDeviceId("device");
        assertEquals("device", createSessionRequest.getDeviceId());

        AuthResponse authResponse = new AuthResponse(true);
        assertTrue(authResponse.isValid());

        AuthRequest authRequest = new AuthRequest();
        authRequest.setTelegramId(7L);
        assertEquals(7L, authRequest.getTelegramId());

        UserProfileResponse userProfileResponse = new UserProfileResponse();
        userProfileResponse.setUsername("user");
        assertEquals("user", userProfileResponse.getUsername());

        Vacancy vacancy = new Vacancy("v1", 100L, "title");
        assertEquals("v1", vacancy.getId());
        assertEquals(100L, vacancy.getUserTelegramId());
        assertEquals("title", vacancy.getTitle());

        UserSettings settings = new UserSettings(55L);
        assertEquals(55L, settings.getTelegramId());
        assertNotNull(settings.getWorkTypes());
        assertNotNull(settings.getCountries());
    }
}
