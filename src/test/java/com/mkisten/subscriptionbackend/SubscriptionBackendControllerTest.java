package com.mkisten.subscriptionbackend;

import com.mkisten.subscription.contract.dto.payment.CreatePaymentRequestDto;
import com.mkisten.subscription.contract.dto.payment.PaymentResponseDto;
import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import com.mkisten.subscriptionbackend.controller.*;
import com.mkisten.subscriptionbackend.entity.*;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import com.mkisten.subscriptionbackend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionBackendControllerTest {

    @Test
    void authControllerGetToken() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        SubscriptionStatusService statusService = mock(SubscriptionStatusService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthController controller = new AuthController(jwtUtil, userService, statusService, passwordEncoder);

        User user = new User();
        user.setTelegramId(1L);
        when(userService.findByTelegramIdOptional(1L)).thenReturn(java.util.Optional.of(user));
        when(userService.getOrCreateService(user, ServiceCode.VACANCY)).thenReturn(new UserServiceSubscription());
        when(jwtUtil.generateToken(1L)).thenReturn("t");

        ResponseEntity<?> response = controller.getToken(1L, ServiceCode.VACANCY);
        assertEquals("t", ((com.mkisten.subscription.contract.dto.auth.TokenResponseDto) response.getBody()).getToken());
    }

    @Test
    void authControllerRefreshToken() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        SubscriptionStatusService statusService = mock(SubscriptionStatusService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthController controller = new AuthController(jwtUtil, userService, statusService, passwordEncoder);

        when(jwtUtil.refreshToken("old")).thenReturn("new");

        ResponseEntity<?> response = controller.refreshToken("Bearer old");
        assertEquals("new", ((com.mkisten.subscription.contract.dto.auth.TokenResponseDto) response.getBody()).getToken());
    }

    @Test
    void subscriptionControllerGetStatus() {
        UserService userService = mock(UserService.class);
        TelegramAuthService telegramAuthService = mock(TelegramAuthService.class);
        SubscriptionStatusService statusService = mock(SubscriptionStatusService.class);
        SubscriptionController controller = new SubscriptionController(userService, telegramAuthService, statusService);

        User user = new User();
        user.setTelegramId(1L);
        user.setRole(UserRole.USER);
        UserServiceSubscription subscription = new UserServiceSubscription();
        subscription.setUser(user);
        subscription.setServiceCode(ServiceCode.VACANCY);
        subscription.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        when(userService.getOrCreateService(user, ServiceCode.VACANCY)).thenReturn(subscription);
        when(telegramAuthService.isSubscriptionActive(subscription)).thenReturn(true);
        when(telegramAuthService.getDaysRemaining(subscription)).thenReturn(3);

        ResponseEntity<?> response = controller.getSubscriptionStatus(user, ServiceCode.VACANCY);
        com.mkisten.subscription.contract.dto.subscription.SubscriptionStatusDto dto =
                (com.mkisten.subscription.contract.dto.subscription.SubscriptionStatusDto) response.getBody();
        assertTrue(dto.getActive());
    }

    @Test
    void telegramAuthControllerCreateSession() {
        TelegramBotService telegramBotService = mock(TelegramBotService.class);
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        TelegramAuthController controller = new TelegramAuthController(telegramBotService, authSessionService, jwtUtil);

        AuthSession session = new AuthSession("s", "d", AuthSession.AuthStatus.PENDING);
        when(authSessionService.createSession("d")).thenReturn(session);

        com.mkisten.subscription.contract.dto.telegram.CreateSessionRequestDto req =
                new com.mkisten.subscription.contract.dto.telegram.CreateSessionRequestDto();
        req.setDeviceId("d");

        ResponseEntity<?> response = controller.createSession(req);
        com.mkisten.subscription.contract.dto.telegram.SessionStatusDto body =
                (com.mkisten.subscription.contract.dto.telegram.SessionStatusDto) response.getBody();
        assertEquals("s", body.getSessionId());
    }

    @Test
    void userBotControllerSendNotification() {
        TelegramBotService telegramBotService = mock(TelegramBotService.class);
        UserService userService = mock(UserService.class);
        UserBotController controller = new UserBotController(telegramBotService, userService);

        User user = new User();
        user.setTelegramId(1L);

        ResponseEntity<?> response = controller.sendNotificationToSelf(user, Map.of("message", "hi"));
        assertEquals(200, response.getStatusCode().value());
        verify(telegramBotService).sendTextMessageToUser(1L, "hi");
    }

    @Test
    void testControllerHello() {
        TestController controller = new TestController();
        Map<String, String> response = controller.hello();
        assertEquals("Hello World!", response.get("message"));
    }

    @Test
    void botManagementControllerStats() {
        TelegramBotService telegramBotService = mock(TelegramBotService.class);
        UserService userService = mock(UserService.class);
        BotMessageService botMessageService = mock(BotMessageService.class);
        BotManagementController controller = new BotManagementController(telegramBotService, userService, botMessageService);

        UserServiceSubscription subscription = new UserServiceSubscription();
        subscription.setUser(new User());
        subscription.setServiceCode(ServiceCode.VACANCY);
        when(userService.getAllUserServices(ServiceCode.VACANCY)).thenReturn(List.of(subscription));
        when(userService.getActiveSubscriptions(ServiceCode.VACANCY)).thenReturn(List.of());
        when(botMessageService.getTotalMessages()).thenReturn(5L);
        when(botMessageService.getMessagesToday()).thenReturn(2L);

        ResponseEntity<?> response = controller.getBotStats();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.get("totalUsers"));
    }

    @Test
    void paymentControllerCreatePayment() {
        PaymentService paymentService = mock(PaymentService.class);
        UserService userService = mock(UserService.class);
        PaymentController controller = new PaymentController(paymentService, userService);

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("user");

        User user = new User();
        user.setTelegramId(1L);
        when(userService.findByUsername("user")).thenReturn(user);

        Payment payment = new Payment(1L, 100.0, SubscriptionPlan.TRIAL, 1, ServiceCode.VACANCY);
        payment.setId(10L);
        when(paymentService.createPayment(1L, SubscriptionPlan.TRIAL, 1, ServiceCode.VACANCY)).thenReturn(payment);

        CreatePaymentRequestDto req = new CreatePaymentRequestDto();
        req.setPlan(SubscriptionPlanDto.TRIAL);
        req.setMonths(1);
        req.setService(com.mkisten.subscription.contract.enums.ServiceCodeDto.VACANCY);

        ResponseEntity<PaymentResponseDto> response = controller.createPayment(req, auth);
        assertNotNull(response.getBody());
    }

    @Test
    void adminPaymentControllerGetPending() {
        PaymentService paymentService = mock(PaymentService.class);
        AdminPaymentController controller = new AdminPaymentController(paymentService);

        when(paymentService.getPendingPayments()).thenReturn(List.of(new Payment()));

        ResponseEntity<?> response = controller.getPendingPayments();
        assertEquals(1, ((List<?>) response.getBody()).size());
    }

    @Test
    void adminControllerGetAllUsers() {
        UserService userService = mock(UserService.class);
        TelegramAuthService telegramAuthService = mock(TelegramAuthService.class);
        PaymentService paymentService = mock(PaymentService.class);
        AdminController controller = new AdminController(userService, telegramAuthService, paymentService);

        User user = new User();
        user.setTelegramId(1L);
        user.setRole(UserRole.USER);
        UserServiceSubscription subscription = new UserServiceSubscription();
        subscription.setUser(user);
        subscription.setServiceCode(ServiceCode.VACANCY);
        subscription.setSubscriptionPlan(SubscriptionPlan.TRIAL);

        when(userService.getAllUserServices(ServiceCode.VACANCY)).thenReturn(List.of(subscription));
        when(telegramAuthService.isSubscriptionActive(subscription)).thenReturn(true);
        when(telegramAuthService.getDaysRemaining(subscription)).thenReturn(3);

        ResponseEntity<?> response = controller.getAllUsers(ServiceCode.VACANCY);
        assertEquals(200, response.getStatusCode().value());
    }
}
