package com.mkisten.subscriptionbackend;

import com.mkisten.subscriptionbackend.entity.*;
import com.mkisten.subscriptionbackend.repository.AuthSessionRepository;
import com.mkisten.subscriptionbackend.repository.BotMessageRepository;
import com.mkisten.subscriptionbackend.repository.PaymentRepository;
import com.mkisten.subscriptionbackend.repository.UserRepository;
import com.mkisten.subscriptionbackend.repository.UserServiceSubscriptionRepository;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import com.mkisten.subscriptionbackend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionBackendServiceTest {

    @Test
    void subscriptionCalculatorImplCalculatesActive() {
        SubscriptionCalculatorImpl calculator = new SubscriptionCalculatorImpl();
        User user = new User();
        user.setTelegramId(1L);
        UserServiceSubscription subscription = new UserServiceSubscription();
        subscription.setUser(user);
        subscription.setServiceCode(ServiceCode.VACANCY);
        subscription.setSubscriptionEndDate(LocalDate.now());

        assertTrue(calculator.calculateSubscriptionActive(subscription));
        assertTrue(calculator.getDaysRemaining(subscription) >= 0);
    }

    @Test
    void telegramAuthServiceHandlesInvalidInitData() {
        SubscriptionCalculator calculator = mock(SubscriptionCalculator.class);
        TelegramAuthService service = new TelegramAuthService(calculator);
        ReflectionTestUtils.setField(service, "botToken", "token");

        assertFalse(service.validateTelegramInitData(""));
        assertNull(service.extractTelegramId("hash=abc"));
    }

    @Test
    void userServiceCreateUserUsesCalculator() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceSubscriptionRepository userServiceRepository = mock(UserServiceSubscriptionRepository.class);
        SubscriptionCalculator calculator = mock(SubscriptionCalculator.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(calculator.calculateSubscriptionActive(any())).thenReturn(true);
        UserService service = new UserService(userRepository, userServiceRepository, calculator, passwordEncoder);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userServiceRepository.findByUserAndServiceCode(any(User.class), any()))
                .thenReturn(Optional.empty());
        when(userServiceRepository.save(any(UserServiceSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.createUser(1L, "First", "Last", "user");
        assertEquals(SubscriptionPlan.TRIAL, user.getSubscriptionPlan());

        ArgumentCaptor<UserServiceSubscription> captor = ArgumentCaptor.forClass(UserServiceSubscription.class);
        verify(userServiceRepository, times(2)).save(captor.capture());
        UserServiceSubscription lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertTrue(lastSaved.isActive());
        assertEquals(SubscriptionPlan.TRIAL, lastSaved.getSubscriptionPlan());
    }

    @Test
    void subscriptionStatusServiceUpdatesUserStatus() {
        UserService userService = mock(UserService.class);
        TelegramAuthService telegramAuthService = mock(TelegramAuthService.class);
        SubscriptionStatusService statusService = new SubscriptionStatusService(userService, telegramAuthService);

        User user = new User();
        user.setTelegramId(1L);
        UserServiceSubscription subscription = new UserServiceSubscription();
        subscription.setUser(user);
        subscription.setServiceCode(ServiceCode.VACANCY);
        subscription.setActive(false);

        when(telegramAuthService.calculateSubscriptionActive(subscription)).thenReturn(true);

        statusService.updateUserSubscriptionStatus(subscription);

        assertTrue(subscription.isActive());
    }

    @Test
    void botMessageServiceLogsMessage() {
        BotMessageRepository repository = mock(BotMessageRepository.class);
        BotMessageService service = new BotMessageService(repository);

        service.logMessage(1L, "TEXT", "hi", "OUT");

        verify(repository).save(any(BotMessage.class));
    }

    @Test
    void paymentServiceCreatePaymentCalculatesAmount() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        UserService userService = mock(UserService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PaymentService service = new PaymentService(paymentRepository, userService, publisher);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.createPayment(1L, SubscriptionPlan.MONTHLY, 2, ServiceCode.VACANCY);
        assertEquals(598.0, payment.getAmount());
        assertEquals(Payment.PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void paymentServiceVerifyPaymentPublishesEvent() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        UserService userService = mock(UserService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PaymentService service = new PaymentService(paymentRepository, userService, publisher);

        Payment payment = new Payment(1L, 100.0, SubscriptionPlan.MONTHLY, 1, ServiceCode.VACANCY);
        payment.setId(5L);

        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(userService.findByTelegramId(1L)).thenReturn(new User());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment verified = service.verifyPayment(5L, "ok");

        assertEquals(Payment.PaymentStatus.VERIFIED, verified.getStatus());
        verify(publisher).publishEvent(any());
    }

    @Test
    void authSessionServiceCreateSessionSetsFields() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AuthSessionService service = new AuthSessionService(repository, jwtUtil);

        when(repository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthSession session = service.createAuthSession("device");
        assertEquals("device", session.getDeviceId());
        assertEquals(AuthSession.AuthStatus.PENDING, session.getStatus());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getExpiresAt());
    }

    @Test
    void authSessionServiceCheckAuthStatusNotFound() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AuthSessionService service = new AuthSessionService(repository, jwtUtil);

        when(repository.findBySessionIdAndDeviceId("s", "d")).thenReturn(Optional.empty());

        AuthSessionService.AuthStatusResponse response = service.checkAuthStatus("s", "d");
        assertEquals(AuthSessionService.AuthStatus.NOT_FOUND, response.getStatus());
    }

    @Test
    void adminSetupServiceCallsUserService() {
        UserService userService = mock(UserService.class);
        AdminSetupService service = new AdminSetupService(userService);

        service.setupDefaultAdmin();

        verify(userService).setUserRole(6927880904L, UserRole.ADMIN);
    }

    @Test
    void telegramBotServiceGeneratesLinks() {
        UserService userService = mock(UserService.class);
        TelegramAuthService telegramAuthService = mock(TelegramAuthService.class);
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BotMessageService botMessageService = mock(BotMessageService.class);
        SupportMessageService supportMessageService = mock(SupportMessageService.class);

        TelegramBotService service = new TelegramBotService(
                userService, telegramAuthService, authSessionService, jwtUtil,
                paymentRepository, publisher, botMessageService, supportMessageService);
        ReflectionTestUtils.setField(service, "botUsername", "mybot");

        String link = service.generateAuthDeepLink("s", "d");
        assertTrue(link.contains("mybot"));

        String subLink = service.generateSubscriptionLink(SubscriptionPlan.TRIAL);
        assertTrue(subLink.contains("trial"));
    }
}
