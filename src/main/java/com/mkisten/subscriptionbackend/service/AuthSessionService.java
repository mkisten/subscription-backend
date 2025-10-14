package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.AuthSession;
import com.mkisten.subscriptionbackend.repository.AuthSessionRepository;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final AuthSessionRepository authSessionRepository;
    private final JwtUtil jwtUtil;

    public AuthSession createTelegramAuthSession(String deviceId) {
        String sessionId = UUID.randomUUID().toString();
        AuthSession session = new AuthSession(sessionId, deviceId, AuthSession.AuthStatus.PENDING);
        return authSessionRepository.save(session);
    }

    public AuthSession completeAuthSession(String sessionId, Long telegramId, String jwtToken) {
        AuthSession session = authSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // Дополнительные проверки
        if (session.getStatus() != AuthSession.AuthStatus.PENDING) {
            throw new RuntimeException("Session already completed or expired: " + sessionId);
        }

        session.setTelegramId(telegramId);
        session.setJwtToken(jwtToken);
        session.setStatus(AuthSession.AuthStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());

        return authSessionRepository.save(session);
    }


    // Перегруженный метод с проверкой deviceId
    @Transactional
    public AuthSession completeAuthSession(String sessionId, String deviceId, Long telegramId, String jwtToken) {
        AuthSession session = authSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        // Проверяем соответствие deviceId
        if (!session.getDeviceId().equals(deviceId)) {
            throw new RuntimeException("Device ID mismatch for session: " + sessionId);
        }

        if (session.getStatus() != AuthSession.AuthStatus.PENDING) {
            throw new RuntimeException("Session already completed or expired: " + sessionId);
        }

        session.setTelegramId(telegramId);
        session.setJwtToken(jwtToken);
        session.setStatus(AuthSession.AuthStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());

        return authSessionRepository.save(session);
    }
//
//    public AuthSession getSession(String sessionId) {
//        return authSessionRepository.findById(sessionId)
//                .orElseThrow(() -> new RuntimeException("Session not found"));
//    }
//
//    public boolean isValidSession(String sessionId) {
//        return authSessionRepository.findById(sessionId)
//                .map(session -> session.getStatus() == AuthSession.AuthStatus.PENDING &&
//                        session.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(10)))
//                .orElse(false);
//    }

    @Transactional
    public AuthSession createAuthSession(String deviceId) {
        try {
            AuthSession session = new AuthSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setDeviceId(deviceId);
            session.setStatus(AuthSession.AuthStatus.PENDING);
            session.setCreatedAt(LocalDateTime.now());
            session.setExpiresAt(LocalDateTime.now().plusMinutes(5)); // 5 минут

            AuthSession savedSession = authSessionRepository.save(session);

            log.info("Created new auth session - ID: {}, Device: {}, Status: {}",
                    savedSession.getSessionId(), savedSession.getDeviceId(), savedSession.getStatus());

            // Логируем для проверки
            Optional<AuthSession> verifiedSession = authSessionRepository.findBySessionId(savedSession.getSessionId());
            if (verifiedSession.isPresent()) {
                log.info("Session verified in database: {}", verifiedSession.get().getSessionId());
            } else {
                log.error("Session NOT FOUND immediately after creation: {}", savedSession.getSessionId());
            }

            return savedSession;
        } catch (Exception e) {
            log.error("Error creating auth session for device: {}", deviceId, e);
            throw new RuntimeException("Failed to create auth session", e);
        }
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        log.info("Searching for session by ID: {}", sessionId);
        Optional<AuthSession> session = authSessionRepository.findBySessionId(sessionId);

        if (session.isPresent()) {
            log.info("Session found: {}", session.get());
        } else {
            log.warn("Session not found: {}", sessionId);
            // Логируем все существующие сессии для отладки
            List<AuthSession> allSessions = authSessionRepository.findAll();
            log.info("Total sessions in database: {}", allSessions.size());
            allSessions.forEach(s -> log.info("Existing session: {} - {}", s.getSessionId(), s.getStatus()));
        }

        return session;
    }

    @Transactional(readOnly = true)
    public AuthStatusResponse checkAuthStatus(String sessionId, String deviceId) {
        log.info("Checking auth status - Session: {}, Device: {}", sessionId, deviceId);

        Optional<AuthSession> authSessionOpt = authSessionRepository.findBySessionIdAndDeviceId(sessionId, deviceId);

        if (authSessionOpt.isEmpty()) {
            log.warn("Session not found: {}", sessionId);
            return new AuthStatusResponse(AuthStatus.NOT_FOUND, "Сессия не найдена", null, null);
        }

        AuthSession authSession = authSessionOpt.get();

        if (authSession.getStatus() == AuthSession.AuthStatus.COMPLETED) {
            // Удаляем использованную сессию
            authSessionRepository.delete(authSession);
            log.info("Auth completed successfully for session: {}", sessionId);
            return new AuthStatusResponse(
                    AuthStatus.COMPLETED,
                    "Авторизация завершена",
                    authSession.getJwtToken(),
                    authSession.getTelegramId()
            );
        }

        if (authSession.getStatus() == AuthSession.AuthStatus.EXPIRED) {
            authSessionRepository.delete(authSession);
            log.info("Auth session expired: {}", sessionId);
            return new AuthStatusResponse(AuthStatus.NOT_FOUND, "Сессия устарела", null, null);
        }
        
        // Если сессия все еще в ожидании
        return new AuthStatusResponse(AuthStatus.PENDING, "Ожидание авторизации", null, null);
    }

    @Transactional(readOnly = true)
    public AuthSession getSession(String sessionId) {
        return authSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
    }

    @Transactional(readOnly = true)
    public boolean isValidSession(String sessionId) {
        try {
            AuthSession session = getSession(sessionId);
            return session.getStatus() == AuthSession.AuthStatus.PENDING && 
                   session.getCreatedAt().isAfter(LocalDateTime.now().minusHours(1));
        } catch (Exception e) {
            log.warn("Invalid session: {}", sessionId, e);
            return false;
        }
    }

    @Transactional
    public void completeAllPendingSessionsForUser(Long telegramId) {
        try {
            // Находим все pending сессии
            List<AuthSession> pendingSessions = authSessionRepository.findByStatus(AuthSession.AuthStatus.PENDING);

            if (pendingSessions.isEmpty()) {
                log.info("No pending sessions found for user: {}", telegramId);
                return;
            }

            // Генерируем JWT токен
            String jwtToken = jwtUtil.generateToken(telegramId);

            // Завершаем все pending сессии
            for (AuthSession session : pendingSessions) {
                session.setStatus(AuthSession.AuthStatus.COMPLETED);
                session.setTelegramId(telegramId);
                session.setJwtToken(jwtToken);
                log.info("Completed pending session: {} for user: {}", session.getSessionId(), telegramId);
            }

            // Сохраняем изменения
            authSessionRepository.saveAll(pendingSessions);
            log.info("Completed {} pending sessions for user: {}", pendingSessions.size(), telegramId);

        } catch (Exception e) {
            log.error("Error completing pending sessions for user: {}", telegramId, e);
            throw e;
        }
    }

    // Очистка устаревших сессий каждые 10 минут
    @Scheduled(fixedRate = 300000) // Каждые 5 минут
    @Transactional
    public void cleanupExpiredSessions() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(10); // Удаляем сессии старше 10 минут
            List<AuthSession> expiredSessions = authSessionRepository.findByStatusAndCreatedAtBefore(
                    AuthSession.AuthStatus.PENDING, cutoffTime);

            if (!expiredSessions.isEmpty()) {
                log.info("Cleaning up {} expired sessions", expiredSessions.size());
                authSessionRepository.deleteAll(expiredSessions);
            }
        } catch (Exception e) {
            log.error("Error cleaning up expired sessions", e);
        }
    }

    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debugInfo = new HashMap<>();

        long totalSessions = authSessionRepository.count();
        long pendingSessions = authSessionRepository.countPendingSessions();

        debugInfo.put("totalSessions", totalSessions);
        debugInfo.put("pendingSessions", pendingSessions);

        List<AuthSession> allSessions = authSessionRepository.findAll();
        List<Map<String, Object>> sessionsInfo = new ArrayList<>();

        for (AuthSession session : allSessions) {
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("sessionId", session.getSessionId());
            sessionInfo.put("deviceId", session.getDeviceId());
            sessionInfo.put("status", session.getStatus());
            sessionInfo.put("telegramId", session.getTelegramId());
            sessionInfo.put("createdAt", session.getCreatedAt());
            sessionInfo.put("updatedAt", session.getUpdatedAt());
            sessionsInfo.add(sessionInfo);
        }

        debugInfo.put("sessions", sessionsInfo);

        return debugInfo;
    }

    // DTO классы
    public enum AuthStatus {
        PENDING,
        COMPLETED,
        NOT_FOUND,
        INVALID_DEVICE
    }

    @Getter
    @RequiredArgsConstructor
    public static class AuthStatusResponse {
        private final AuthStatus status;
        private final String message;
        private final String jwtToken;
        private final Long telegramId;
    }
}