package com.mkisten.subscriptionbackend;

import com.mkisten.subscriptionbackend.dto.*;
import com.mkisten.subscriptionbackend.entity.*;
import com.mkisten.subscriptionbackend.exception.InvalidTokenException;
import com.mkisten.subscriptionbackend.exception.SubscriptionExpiredException;
import com.mkisten.subscriptionbackend.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionBackendModelTest {

    @Test
    void userDetailsBehavior() {
        User user = new User();
        user.setRole(UserRole.ADMIN);
        user.setTelegramId(1L);
        user.setActive(true);

        assertTrue(user.isActive());
        assertEquals("ROLE_ADMIN", user.getAuthorities().iterator().next().getAuthority());
        assertTrue(user.isAccountNonExpired());
    }

    @Test
    void enumValues() {
        assertEquals(SubscriptionPlan.TRIAL, SubscriptionPlan.valueOf("TRIAL"));
        assertEquals(UserRole.USER, UserRole.valueOf("USER"));
        assertEquals(Platform.WEB, Platform.valueOf("WEB"));
    }

    @Test
    void dtoAccessors() {
        MessageResponse messageResponse = new MessageResponse("ok");
        assertEquals("ok", messageResponse.getMessage());

        ExtendSubscriptionRequest extendRequest = new ExtendSubscriptionRequest();
        extendRequest.setDays(7);
        extendRequest.setPlan(SubscriptionPlan.TRIAL);
        assertEquals(7, extendRequest.getDays());

        ErrorResponse errorResponse = new ErrorResponse("code", "msg");
        assertEquals("code", errorResponse.getError());

        CreateSessionRequest createSessionRequest = new CreateSessionRequest();
        createSessionRequest.setDeviceId("device");
        assertEquals("device", createSessionRequest.getDeviceId());

        CreatePaymentRequest createPaymentRequest = new CreatePaymentRequest();
        createPaymentRequest.setPlan(SubscriptionPlan.MONTHLY);
        createPaymentRequest.setMonths(1);
        createPaymentRequest.setService(ServiceCode.VACANCY);
        assertEquals(SubscriptionPlan.MONTHLY, createPaymentRequest.getPlan());

        CancelSubscriptionRequest cancelRequest = new CancelSubscriptionRequest();
        cancelRequest.setEmail("a@b.c");
        assertEquals("a@b.c", cancelRequest.getEmail());

        AuthResponse authResponse = new AuthResponse(
                "t", 1L, "f", "l", "u", "e", "p", LocalDate.now(),
                true, SubscriptionPlan.TRIAL, true, 1, "USER");
        assertEquals("t", authResponse.getToken());

        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        subscriptionResponse.setActive(true);
        assertTrue(subscriptionResponse.isActive());

        PaymentResponse paymentResponse = new PaymentResponse(true, "ok", "1", null);
        assertTrue(paymentResponse.isSuccess());

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPlan("MONTHLY");
        assertEquals("MONTHLY", paymentRequest.getPlan());

        UserDto userDto = UserDto.builder().telegramId(5L).build();
        assertEquals(5L, userDto.getTelegramId());

        SubscriptionStatusResponse statusResponse = new SubscriptionStatusResponse();
        statusResponse.setTelegramId(3L);
        statusResponse.setActive(true);
        assertTrue(statusResponse.isActive());

        UserResponse userResponse = new UserResponse("a@b.c", LocalDate.now().plusDays(1), SubscriptionPlan.TRIAL, true);
        assertEquals("a@b.c", userResponse.getEmail());
    }

    @Test
    void entityAccessors() {
        AuthSession session = new AuthSession("s1", "d1", AuthSession.AuthStatus.PENDING);
        assertEquals("s1", session.getSessionId());

        BotMessage message = new BotMessage();
        message.setTelegramId(1L);
        message.setContent("hi");
        assertEquals("hi", message.getContent());

        Payment payment = new Payment(1L, 100.0, SubscriptionPlan.TRIAL, 1, ServiceCode.VACANCY);
        assertEquals(100.0, payment.getAmount());

        PaymentHistory history = new PaymentHistory();
        history.setAmount(java.math.BigDecimal.TEN);
        history.setStatus(PaymentHistory.PaymentStatus.PENDING);
        assertEquals(java.math.BigDecimal.TEN, history.getAmount());

        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setId(1L);
        telegramUser.setUsername("u");
        assertEquals("u", telegramUser.getUsername());

        TelegramWebAppData webAppData = TelegramWebAppData.builder()
                .initData("init")
                .deviceId("device")
                .platform(Platform.WEB)
                .build();
        assertEquals("init", webAppData.getInitData());

        User user = new User();
        user.setSubscriptionEndDate(LocalDate.now());
        assertNotNull(user.getSubscriptionEndDate());
    }

    @Test
    void exceptionMessages() {
        UserNotFoundException notFound = new UserNotFoundException("missing");
        InvalidTokenException invalidToken = new InvalidTokenException("bad");
        SubscriptionExpiredException expired = new SubscriptionExpiredException("expired");

        assertEquals("missing", notFound.getMessage());
        assertEquals("bad", invalidToken.getMessage());
        assertEquals("expired", expired.getMessage());
    }
}
