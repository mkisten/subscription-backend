//package com.mkisten.subscriptionbackend.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.mkisten.subscriptionbackend.dto.AuthResponse;
//import com.mkisten.subscriptionbackend.dto.UserDto;
//import com.mkisten.subscriptionbackend.entity.*;
//import com.mkisten.subscriptionbackend.repository.AuthSessionRepository;
//import com.mkisten.subscriptionbackend.repository.UserRepository;
//import com.mkisten.subscriptionbackend.security.JwtUtil;
//import jakarta.transaction.Transactional;
//import javafx.application.Platform;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import javax.naming.AuthenticationException;
//import java.net.URLDecoder;
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class UniversalAuthService {
//
//    private final UserRepository userRepository;
//    private final JwtUtil jwtUtil;
//    private final AuthSessionRepository authSessionRepository;
//
//    @Value("${telegram.bot.token}")
//    private String botToken;
//
//    /**
//     * Универсальный метод авторизации через Telegram Web App
//     * Работает для Web, Android, Windows
//     */
//    @Transactional
//    public AuthResponse authenticateWithTelegramWebApp(TelegramWebAppData webAppData) throws AuthenticationException {
//        try {
//            // 1. Валидация данных от Telegram
//            if (!validateTelegramWebAppData(webAppData.getInitData())) {
//                throw new SecurityException("Invalid Telegram data");
//            }
//
//            // 2. Извлечение данных пользователя
//            TelegramUser telegramUser = extractUserData(webAppData.getInitData());
//
//            // 3. Поиск или создание пользователя
//            User user = findOrCreateUser(telegramUser);
//
//            // 4. Генерация JWT токена
//            String jwtToken = jwtUtil.generateToken(user.getTelegramId());
//            String refreshToken = jwtUtil.generateRefreshToken(user.getTelegramId());
//
//            // 5. Сохранение сессии
//            saveAuthSession(user, webAppData.getDeviceId(), webAppData.getPlatform());
//
//            log.info("User authenticated: {} from platform: {}",
//                    user.getTelegramId(), webAppData.getPlatform());
//
//            return AuthResponse.builder()
//                    .success(true)
//                    .jwtToken(jwtToken)
//                    .refreshToken(refreshToken)
//                    .user(mapToUserDto(user))
//                    .build();
//
//        } catch (Exception e) {
//            log.error("Authentication failed", e);
//            throw new AuthenticationException("Authentication failed: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Валидация данных от Telegram Web App
//     */
//    private boolean validateTelegramWebAppData(String initData) {
//        try {
//            Map<String, String> dataMap = parseInitData(initData);
//            String receivedHash = dataMap.remove("hash");
//
//            if (receivedHash == null) {
//                return false;
//            }
//
//            // Создаем data-check-string
//            String dataCheckString = dataMap.entrySet().stream()
//                    .sorted(Map.Entry.comparingByKey())
//                    .map(e -> e.getKey() + "=" + e.getValue())
//                    .collect(Collectors.joining("\n"));
//
//            // Вычисляем секретный ключ
//            byte[] secretKey = hmacSha256("WebAppData".getBytes(), botToken.getBytes());
//
//            // Вычисляем хеш
//            byte[] hash = hmacSha256(dataCheckString.getBytes(), secretKey);
//            String calculatedHash = bytesToHex(hash);
//
//            // Проверяем время (данные должны быть не старше 1 часа)
//            long authDate = Long.parseLong(dataMap.get("auth_date"));
//            long currentTime = System.currentTimeMillis() / 1000;
//            if (currentTime - authDate > 3600) {
//                log.warn("Auth data expired");
//                return false;
//            }
//
//            return calculatedHash.equals(receivedHash);
//
//        } catch (Exception e) {
//            log.error("Validation error", e);
//            return false;
//        }
//    }
//
//    /**
//     * Извлечение данных пользователя из initData
//     */
//    private TelegramUser extractUserData(String initData) {
//        try {
//            Map<String, String> dataMap = parseInitData(initData);
//            String userJson = dataMap.get("user");
//
//            if (userJson == null) {
//                throw new IllegalArgumentException("User data not found");
//            }
//
//            // Парсинг JSON (используйте Jackson или Gson)
//            ObjectMapper mapper = new ObjectMapper();
//            return mapper.readValue(userJson, TelegramUser.class);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to extract user data", e);
//        }
//    }
//
//    /**
//     * Поиск или создание пользователя
//     */
//    private User findOrCreateUser(TelegramUser telegramUser) {
//        return userRepository.findByTelegramId(telegramUser.getId())
//                .map(user -> {
//                    // Обновляем данные при каждом входе
//                    user.setFirstName(telegramUser.getFirstName());
//                    user.setLastName(telegramUser.getLastName());
//                    user.setUsername(telegramUser.getUsername());
//                    user.setLastLoginAt(LocalDateTime.now());
//                    return userRepository.save(user);
//                })
//                .orElseGet(() -> {
//                    // Создаем нового пользователя с триальным периодом
//                    User newUser = new User();
//                    newUser.setTelegramId(telegramUser.getId());
//                    newUser.setFirstName(telegramUser.getFirstName());
//                    newUser.setLastName(telegramUser.getLastName());
//                    newUser.setUsername(telegramUser.getUsername());
//                    newUser.setSubscriptionPlan(SubscriptionPlan.TRIAL);
//                    newUser.setTrialUsed(true);
//                    newUser.setSubscriptionEndDate(LocalDate.now().plusDays(7));
//                    newUser.setCreatedAt(LocalDateTime.now());
//                    newUser.setLastLoginAt(LocalDateTime.now());
//                    newUser.setRole(UserRole.USER);
//
//                    log.info("New user registered: {}", telegramUser.getId());
//                    return userRepository.save(newUser);
//                });
//    }
//
//    /**
//     * Сохранение информации о сессии
//     */
//    private void saveAuthSession(User user, String deviceId, Platform platform) {
//        AuthSession session = new AuthSession();
//        session.setUserId(user.getTelegramId());
//        session.setDeviceId(deviceId);
//        session.setPlatform(platform);
//        session.setCreatedAt(LocalDateTime.now());
//        session.setExpiresAt(LocalDateTime.now().plusDays(30));
//        authSessionRepository.save(session);
//    }
//
//    private Map<String, String> parseInitData(String initData) {
//        Map<String, String> result = new HashMap<>();
//        String[] pairs = initData.split("&");
//
//        for (String pair : pairs) {
//            String[] keyValue = pair.split("=", 2);
//            if (keyValue.length == 2) {
//                result.put(
//                        keyValue[0],
//                        URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8)
//                );
//            }
//        }
//
//        return result;
//    }
//
//    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
//        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
//        SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
//        sha256Hmac.init(secretKey);
//        return sha256Hmac.doFinal(data);
//    }
//
//    private String bytesToHex(byte[] bytes) {
//        StringBuilder result = new StringBuilder();
//        for (byte b : bytes) {
//            result.append(String.format("%02x", b));
//        }
//        return result.toString();
//    }
//
//    private UserDto mapToUserDto(User user) {
//        return UserDto.builder()
//                .telegramId(user.getTelegramId())
//                .firstName(user.getFirstName())
//                .lastName(user.getLastName())
//                .username(user.getUsername())
//                .subscriptionPlan(user.getSubscriptionPlan())
//                .subscriptionEndDate(user.getSubscriptionEndDate())
//                .isActive(user.getSubscriptionEndDate().isAfter(LocalDate.now()))
//                .build();
//    }
//}
