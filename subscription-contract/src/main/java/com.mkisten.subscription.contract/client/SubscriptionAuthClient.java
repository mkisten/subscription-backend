//package com.mkisten.subscription.contract.client;
//
//
//import com.mkisten.subscription.contract.dto.subscription.SubscriptionStatusDto;
//import com.mkisten.subscription.contract.dto.common.MessageResponseDto;
//
//public interface SubscriptionAuthClient {
//
//    // --- AUTH ---
//
//    AuthLoginResponseDto login(AuthTokenRequestDto request);
//
//    AuthValidateResponseDto validateToken(String jwtToken);
//
//    // --- SUBSCRIPTION ---
//
//    SubscriptionStatusDto getSubscriptionStatus(String jwtToken);
//
//    // --- TELEGRAM AUTH SESSION ---
//
//    SessionStatusResponseDto createSession(CreateSessionRequestDto request);
//
//    SessionStatusResponseDto getSessionStatus(String sessionId);
//
//    // --- BOT NOTIFICATION (опционально) ---
//
//    MessageResponseDto sendBotNotification(String jwtToken, String message);
//}
