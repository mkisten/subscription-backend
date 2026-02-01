package com.mkisten.vacancybackend;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.dto.TokenResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.entity.VacancyStatus;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import com.mkisten.vacancybackend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacancyBackendServiceTest {

    @Test
    void vacancyServiceSaveVacanciesFiltersExisting() {
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        VacancyStreamService vacancyStreamService = mock(VacancyStreamService.class);
        VacancyService service = new VacancyService(vacancyRepository, authServiceClient, vacancyStreamService);

        ProfileResponse profile = new ProfileResponse();
        profile.setTelegramId(10L);
        when(authServiceClient.getCurrentUserProfile("token")).thenReturn(profile);

        Vacancy v1 = new Vacancy();
        v1.setId("1");
        Vacancy v2 = new Vacancy();
        v2.setId("2");

        when(vacancyRepository.findVacancyIdsByUser(10L)).thenReturn(Set.of("1"));
        when(vacancyRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Vacancy> saved = service.saveVacancies("token", List.of(v1, v2));

        assertEquals(1, saved.size());
        Vacancy savedVacancy = saved.get(0);
        assertEquals("2", savedVacancy.getId());
        assertEquals(10L, savedVacancy.getUserTelegramId());
        assertEquals(VacancyStatus.NEW, savedVacancy.getStatus());
        assertFalse(savedVacancy.getSentToTelegram());
        assertNotNull(savedVacancy.getLoadedAt());
    }

    @Test
    void vacancySmartServicePopulatesSettingsAndSendsNotifications() {
        UserSettingsService settingsService = mock(UserSettingsService.class);
        HHruApiService apiService = mock(HHruApiService.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        VacancyService vacancyService = mock(VacancyService.class);

        VacancySmartService service = new VacancySmartService(settingsService, apiService, telegramService, vacancyService);

        UserSettings settings = new UserSettings(10L);
        settings.setSearchQuery("java");
        settings.setDays(3);
        settings.setExcludeKeywords("intern");
        settings.setTelegramNotify(true);
        when(settingsService.getSettings("token")).thenReturn(settings);

        when(apiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(vacancyService.saveVacancies(eq("token"), anyList())).thenReturn(List.of());

        SearchRequest request = new SearchRequest();
        request.setDays(null);
        List<Vacancy> result = service.searchWithUserSettings(request, "token", 10L);

        assertEquals("java", request.getQuery());
        assertEquals(3, request.getDays());
        assertEquals("intern", request.getExcludeKeywords());
        assertEquals(0, result.size());
        verify(telegramService).sendAllUnsentVacanciesToTelegram("token", 10L);
    }

    @Test
    void vacancyAutoUpdaterSkipsWhenTokenMissing() {
        UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
        VacancySmartService smartService = mock(VacancySmartService.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);

        VacancyAutoUpdater updater = new VacancyAutoUpdater(settingsRepository, smartService, authServiceClient);

        UserSettings s1 = new UserSettings(1L);
        UserSettings s2 = new UserSettings(2L);
        when(settingsRepository.findByAutoUpdateEnabledTrue()).thenReturn(List.of(s1, s2));

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken("t");
        when(authServiceClient.getTokenByTelegramId(1L)).thenReturn(tokenResponse);
        when(authServiceClient.getTokenByTelegramId(2L)).thenReturn(null);

        when(smartService.searchWithUserSettings(any(), eq("t"), eq(1L))).thenReturn(List.of());

        updater.updateAllUsers();

        verify(smartService, times(1)).searchWithUserSettings(any(), eq("t"), eq(1L));
        verify(smartService, never()).searchWithUserSettings(any(), eq("t"), eq(2L));
    }

    @Test
    void hhruApiServiceSearchVacanciesMapsResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        HHruApiService service = new HHruApiService(restTemplate, authServiceClient);

        ReflectionTestUtils.setField(service, "baseUrl", "http://example");

        ProfileResponse profile = new ProfileResponse();
        profile.setTelegramId(99L);
        when(authServiceClient.getCurrentUserProfile("token")).thenReturn(profile);

        Map<String, Object> item = new HashMap<>();
        item.put("id", "1");
        item.put("name", "Dev");
        item.put("alternate_url", "http://hh");
        item.put("published_at", LocalDateTime.now().toString());
        item.put("employer", Map.of("name", "Acme"));
        item.put("area", Map.of("name", "City"));
        item.put("schedule", Map.of("name", "remote"));
        item.put("salary", Map.of("from", 100, "to", 200, "currency", "RUR"));

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));

        when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(ResponseEntity.ok(body));

        SearchRequest request = new SearchRequest();
        request.setQuery("dev");
        request.setDays(1);

        List<Vacancy> vacancies = service.searchVacancies(request, "token");
        assertEquals(1, vacancies.size());
        assertEquals("1", vacancies.get(0).getId());
        assertEquals(99L, vacancies.get(0).getUserTelegramId());
    }

    @Test
    void telegramNotificationServiceMarksSent() {
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        TelegramNotificationService service = new TelegramNotificationService(authServiceClient, vacancyRepository);
        ReflectionTestUtils.setField(service, "maxVacanciesPerMessage", 10);

        Vacancy vacancy = new Vacancy();
        vacancy.setId("v1");
        vacancy.setUserTelegramId(1L);
        vacancy.setTitle("title");
        vacancy.setPublishedAt(LocalDateTime.now());
        vacancy.setSentToTelegram(false);

        when(vacancyRepository.findByUserTelegramIdAndSentToTelegramFalseOrderByPublishedAtAsc(1L))
                .thenReturn(List.of(vacancy));

        service.sendAllUnsentVacanciesToTelegram("token", 1L);

        verify(authServiceClient, atLeastOnce()).sendTelegramNotification(eq("token"), anyString());
        verify(vacancyRepository).markAsSentToTelegram(eq(1L), eq(List.of("v1")));
    }

    @Test
    void userSettingsServiceUpdateSettingsSendsNotification() {
        UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        UserSettingsService service = new UserSettingsService(settingsRepository, authServiceClient, telegramService);

        ProfileResponse profile = new ProfileResponse();
        profile.setTelegramId(5L);
        when(authServiceClient.getCurrentUserProfile("token")).thenReturn(profile);

        UserSettings existing = new UserSettings(5L);
        existing.setTelegramNotify(true);
        when(settingsRepository.findByTelegramId(5L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSettings update = new UserSettings();
        update.setSearchQuery("new");

        UserSettings saved = service.updateSettings("token", update);

        assertEquals("new", saved.getSearchQuery());
        verify(telegramService).sendSettingsUpdatedNotification("token");
    }

    @Test
    void userSettingsServiceSubscriptionStatusHandlesException() {
        UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        UserSettingsService service = new UserSettingsService(settingsRepository, authServiceClient, telegramService);

        when(authServiceClient.getSubscriptionStatus("token")).thenThrow(new RuntimeException("fail"));

        assertFalse(service.isSubscriptionActive("token"));
    }
}
