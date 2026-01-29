package com.mkisten.vacancybackend;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.controller.*;
import com.mkisten.vacancybackend.dto.*;
import com.mkisten.vacancybackend.entity.UserSettings;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.entity.VacancyStatus;
import com.mkisten.vacancybackend.service.TelegramNotificationService;
import com.mkisten.vacancybackend.service.UserSettingsService;
import com.mkisten.vacancybackend.service.VacancyService;
import com.mkisten.vacancybackend.service.VacancySmartService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacancyBackendControllerTest {

    @Test
    void authControllerGetTokenByTelegramId() {
        AuthServiceClient client = mock(AuthServiceClient.class);
        AuthController controller = new AuthController(client);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken("t");
        when(client.getTokenByTelegramId(1L)).thenReturn(tokenResponse);

        ResponseEntity<TokenResponse> response = controller.getTokenByTelegramId(1L);
        assertEquals("t", response.getBody().getToken());
    }

    @Test
    void sessionControllerGetSessionInfo() {
        AuthServiceClient client = mock(AuthServiceClient.class);
        UserSettingsService settingsService = mock(UserSettingsService.class);
        SessionController controller = new SessionController(client, settingsService);

        ProfileResponse profile = new ProfileResponse();
        profile.setTelegramId(10L);
        when(client.getCurrentUserProfile("token")).thenReturn(profile);

        SubscriptionStatusResponse statusResponse = new SubscriptionStatusResponse();
        statusResponse.setActive(true);
        when(settingsService.getSubscriptionInfo("token")).thenReturn(statusResponse);

        ResponseEntity<Map<String, Object>> response = controller.getSessionInfo("Bearer token");
        assertEquals(10L, ((ProfileResponse) response.getBody().get("user")).getTelegramId());
        assertEquals(statusResponse, response.getBody().get("subscription"));
    }

    @Test
    void notificationControllerCustomMessageMissing() {
        TelegramNotificationService service = mock(TelegramNotificationService.class);
        NotificationController controller = new NotificationController(service);

        ResponseEntity<Map<String, String>> response = controller.sendCustomNotification("Bearer t", Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void userSettingsControllerSetupAutoUpdateMissingFields() {
        UserSettingsService settingsService = mock(UserSettingsService.class);
        UserSettingsController controller = new UserSettingsController(settingsService);

        ResponseEntity<Map<String, String>> response = controller.setupAutoUpdate("Bearer t", Map.of("enabled", true));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void vacancyControllerGetNewVacanciesCount() {
        VacancyService vacancyService = mock(VacancyService.class);
        VacancySmartService vacancySmartService = mock(VacancySmartService.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        VacancyController controller = new VacancyController(vacancyService, vacancySmartService, userSettingsService);

        when(vacancyService.getNewVacanciesCount("token")).thenReturn(5L);

        ResponseEntity<Long> response = controller.getNewVacanciesCount("Bearer token");
        assertEquals(5L, response.getBody());
    }

    @Test
    void vacancySubscriptionControllerGetStatus() {
        AuthServiceClient client = mock(AuthServiceClient.class);
        VacancySubscriptionController controller = new VacancySubscriptionController(client);

        SubscriptionStatusResponse status = new SubscriptionStatusResponse();
        status.setActive(true);
        when(client.getSubscriptionStatus("token")).thenReturn(status);

        ResponseEntity<SubscriptionStatusResponse> response = controller.getSubscriptionStatus("Bearer token");
        assertTrue(response.getBody().getActive());
    }

    @Test
    void vacancyTelegramAuthControllerCreateSession() {
        AuthServiceClient client = mock(AuthServiceClient.class);
        VacancyTelegramAuthController controller = new VacancyTelegramAuthController(client);

        SessionStatusResponse session = new SessionStatusResponse();
        session.setSessionId("s");
        when(client.createSession(any())).thenReturn(session);

        ResponseEntity<?> response = controller.createAuthSession(new CreateSessionRequest());
        assertEquals(session, response.getBody());
    }

    @Test
    void vacancyControllerSearchVacancies() {
        VacancyService vacancyService = mock(VacancyService.class);
        VacancySmartService vacancySmartService = mock(VacancySmartService.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        VacancyController controller = new VacancyController(vacancyService, vacancySmartService, userSettingsService);

        UserSettings settings = new UserSettings(1L);
        when(userSettingsService.getSettings("token")).thenReturn(settings);

        Vacancy vacancy = new Vacancy();
        vacancy.setId("1");
        vacancy.setStatus(VacancyStatus.NEW);
        when(vacancySmartService.searchWithUserSettings(any(), eq("token"), eq(1L))).thenReturn(List.of(vacancy));

        ResponseEntity<List<VacancyResponse>> response = controller.searchVacancies("Bearer token", new SearchRequest());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void controllerGlobalExceptionHandlerSubscriptionRequired() {
        com.mkisten.vacancybackend.controller.GlobalExceptionHandler handler = new com.mkisten.vacancybackend.controller.GlobalExceptionHandler();
        ResponseEntity<Object> response = handler.handleSubscriptionRequired(
                new com.mkisten.vacancybackend.api.error.SubscriptionRequiredException("msg", "/path"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
