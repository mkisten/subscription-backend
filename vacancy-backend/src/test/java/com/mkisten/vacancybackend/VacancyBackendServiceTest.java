package com.mkisten.vacancybackend;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.dto.SearchRequest;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import com.mkisten.vacancybackend.dto.TokenResponse;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.entity.VacancyStatus;
import com.mkisten.vacancybackend.repository.UserSettingsRepository;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import com.mkisten.vacancybackend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacancyBackendServiceTest {

    @Test
    void vacancyServiceSaveVacanciesFiltersExisting() {
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        VacancyStreamService vacancyStreamService = mock(VacancyStreamService.class);
        VacancyService service = new VacancyService(vacancyRepository, userSettingsRepository, authServiceClient, vacancyStreamService);

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
        HabrCareerApiService habrApiService = mock(HabrCareerApiService.class);
        GetmatchCareerApiService getmatchApiService = mock(GetmatchCareerApiService.class);
        SuperjobCareerApiService superjobApiService = mock(SuperjobCareerApiService.class);
        RabotaByApiService rabotaByApiService = mock(RabotaByApiService.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        VacancyService vacancyService = mock(VacancyService.class);

        VacancySmartService service = new VacancySmartService(
                settingsService,
                apiService,
                habrApiService,
                getmatchApiService,
                superjobApiService,
                rabotaByApiService,
                telegramService,
                vacancyService
        );

        UserSettings settings = new UserSettings(10L);
        settings.setSearchQuery("java");
        settings.setDays(3);
        settings.setExcludeKeywords("intern");
        settings.setTelegramNotify(true);
        when(settingsService.getSettings("token")).thenReturn(settings);
        when(settingsService.isSubscriptionActive("token")).thenReturn(true);

        when(apiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(habrApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(getmatchApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(superjobApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(rabotaByApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
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
    void vacancySmartServiceUsesRabotaByForBelarusRequests() {
        UserSettingsService settingsService = mock(UserSettingsService.class);
        HHruApiService apiService = mock(HHruApiService.class);
        HabrCareerApiService habrApiService = mock(HabrCareerApiService.class);
        GetmatchCareerApiService getmatchApiService = mock(GetmatchCareerApiService.class);
        SuperjobCareerApiService superjobApiService = mock(SuperjobCareerApiService.class);
        RabotaByApiService rabotaByApiService = mock(RabotaByApiService.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        VacancyService vacancyService = mock(VacancyService.class);

        VacancySmartService service = new VacancySmartService(
                settingsService,
                apiService,
                habrApiService,
                getmatchApiService,
                superjobApiService,
                rabotaByApiService,
                telegramService,
                vacancyService
        );

        UserSettings settings = new UserSettings(10L);
        settings.setSearchQuery("java");
        settings.setCountries(Set.of("belarus"));
        settings.setWorkTypes(Set.of());
        settings.setTelegramNotify(false);
        when(settingsService.getSettings("token")).thenReturn(settings);

        when(apiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(habrApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(getmatchApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());
        when(superjobApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of());

        Vacancy rabotaVacancy = new Vacancy();
        rabotaVacancy.setId("rabota-by-1");
        rabotaVacancy.setTitle("Java Developer");
        rabotaVacancy.setSchedule("Удалённо");
        when(rabotaByApiService.searchVacancies(any(), eq("token"))).thenReturn(List.of(rabotaVacancy));
        when(vacancyService.saveVacancies(eq("token"), anyList())).thenReturn(List.of(rabotaVacancy));

        SearchRequest request = new SearchRequest();
        request.setCountries(Set.of("belarus"));
        List<Vacancy> result = service.searchWithUserSettings(request, "token", 10L);

        verify(rabotaByApiService).searchVacancies(any(), eq("token"));
        assertEquals(1, result.size());
        assertEquals("rabota-by-1", result.get(0).getId());
    }

    @Test
    void vacancyAutoUpdaterSkipsWhenTokenMissing() {
        UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
        VacancySmartService smartService = mock(VacancySmartService.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);

        VacancyAutoUpdater updater = new VacancyAutoUpdater(settingsRepository, smartService, authServiceClient);
        ReflectionTestUtils.setField(updater, "workerCount", 1);
        updater.startWorkers();

        UserSettings s1 = new UserSettings(1L);
        s1.setAutoUpdateEnabled(true);
        s1.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        UserSettings s2 = new UserSettings(2L);
        s2.setAutoUpdateEnabled(true);
        s2.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        when(settingsRepository.findDueUsers(any(), any(Pageable.class))).thenReturn(List.of(s1, s2));
        when(settingsRepository.findByTelegramId(1L)).thenReturn(Optional.of(s1));
        when(settingsRepository.findByTelegramId(2L)).thenReturn(Optional.of(s2));

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken("t");
        when(authServiceClient.getTokenByTelegramId(1L)).thenReturn(tokenResponse);
        when(authServiceClient.getTokenByTelegramId(2L)).thenReturn(null);
        SubscriptionStatusResponse active = new SubscriptionStatusResponse();
        active.setActive(true);
        when(authServiceClient.getSubscriptionStatus("t")).thenReturn(active);

        when(smartService.searchWithUserSettings(any(), eq("t"), eq(1L))).thenReturn(List.of());

        updater.updateAllUsers();

        verify(smartService, timeout(1000).times(1)).searchWithUserSettings(any(), eq("t"), eq(1L));
        verify(smartService, after(1000).never()).searchWithUserSettings(any(), eq("t"), eq(2L));
        updater.stopWorkers();
    }

    @Test
    void hhruApiServiceSearchVacanciesMapsResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        HHruApiService service = new HHruApiService(restTemplate, authServiceClient);

        ReflectionTestUtils.setField(service, "baseUrl", "http://example");
        ReflectionTestUtils.setField(service, "maxPages", 1);

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
        body.put("pages", 1);

        when(restTemplate.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

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
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        UserSettingsService service = new UserSettingsService(settingsRepository, authServiceClient, telegramService, vacancyRepository);

        ProfileResponse profile = new ProfileResponse();
        profile.setTelegramId(5L);
        when(authServiceClient.getCurrentUserProfile("token")).thenReturn(profile);

        UserSettings existing = new UserSettings(5L);
        existing.setTelegramNotify(true);
        when(settingsRepository.findByTelegramId(5L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSettings update = new UserSettings();
        update.setSearchQuery("new");
        update.setTelegramNotify(true);

        UserSettings saved = service.updateSettings("token", update);

        assertEquals("new", saved.getSearchQuery());
        verify(telegramService).sendSettingsUpdatedNotification("token");
    }

    @Test
    void userSettingsServiceRemovesVacanciesForExcludedCompaniesByPartialEmployerMatch() {
        UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        UserSettingsService service = new UserSettingsService(settingsRepository, authServiceClient, telegramService, vacancyRepository);

        ProfileResponse profile = new ProfileResponse();
        profile.setTelegramId(5L);
        when(authServiceClient.getCurrentUserProfile("token")).thenReturn(profile);

        UserSettings existing = new UserSettings(5L);
        when(settingsRepository.findByTelegramId(5L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vacancy matching = new Vacancy();
        matching.setId("match-1");
        matching.setEmployer("Yandex Practicum");
        Vacancy other = new Vacancy();
        other.setId("other-1");
        other.setEmployer("EPAM");
        when(vacancyRepository.findByUserTelegramIdOrderByStatusAscLoadedAtDesc(5L))
                .thenReturn(List.of(matching, other));

        UserSettings update = new UserSettings();
        update.setExcludeCompanies("Yandex");

        service.updateSettings("token", update);

        verify(vacancyRepository).deleteByUserAndId(5L, "match-1");
        verify(vacancyRepository, never()).deleteByUserAndId(5L, "other-1");
    }

    @Test
    void userSettingsServiceSubscriptionStatusHandlesException() {
        UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
        AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
        TelegramNotificationService telegramService = mock(TelegramNotificationService.class);
        VacancyRepository vacancyRepository = mock(VacancyRepository.class);
        UserSettingsService service = new UserSettingsService(settingsRepository, authServiceClient, telegramService, vacancyRepository);

        when(authServiceClient.getSubscriptionStatus("token")).thenThrow(new RuntimeException("fail"));

        assertFalse(service.isSubscriptionActive("token"));
    }
}
