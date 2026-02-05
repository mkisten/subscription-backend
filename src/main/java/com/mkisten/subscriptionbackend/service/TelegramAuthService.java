package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    @Value("${telegram.bot.token}")
    private String botToken;

    private final SubscriptionCalculator subscriptionCalculator;

    public boolean isSubscriptionActive(UserServiceSubscription subscription) {
        if (subscription == null) {
            log.warn("Subscription is null in isSubscriptionActive check");
            return false;
        }
        boolean calculatedActive = subscriptionCalculator.calculateSubscriptionActive(subscription);
        log.debug("Subscription status for user {} service {}: calculated={}, endDate={}, today={}",
                subscription.getUser().getTelegramId(), subscription.getServiceCode(), calculatedActive,
                subscription.getSubscriptionEndDate(), LocalDate.now());
        return calculatedActive;
    }

    public boolean calculateSubscriptionActive(UserServiceSubscription subscription) {
        return subscriptionCalculator.calculateSubscriptionActive(subscription);
    }

    public int getDaysRemaining(UserServiceSubscription subscription) {
        return subscriptionCalculator.getDaysRemaining(subscription);
    }

    /**
     * Проверяет валидность данных авторизации Telegram Web App
     */
    public boolean validateTelegramInitData(String initData) {
        try {
            if (initData == null || initData.isEmpty()) {
                log.warn("Empty initData provided");
                return false;
            }

            String[] pairs = initData.split("&");
            String receivedHash = "";
            Map<String, String> dataMap = new HashMap<>();

            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length != 2) continue;

                String key = keyValue[0];
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);

                if ("hash".equals(key)) {
                    receivedHash = value;
                } else {
                    dataMap.put(key, value);
                }
            }

            if (receivedHash.isEmpty()) {
                log.warn("No hash found in initData");
                return false;
            }

            String dataCheckString = dataMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));

            byte[] secretKey = hmacSha256("WebAppData", botToken);
            String calculatedHash = bytesToHex(hmacSha256(dataCheckString, new String(secretKey, StandardCharsets.UTF_8)));

            boolean isValid = calculatedHash.equals(receivedHash);
            log.debug("Telegram initData validation result: {}", isValid);

            return isValid;

        } catch (Exception e) {
            log.error("Error validating Telegram initData", e);
            return false;
        }
    }

    /**
     * Извлекает Telegram ID из initData
     */
    public Long extractTelegramId(String initData) {
        try {
            String[] pairs = initData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && "user".equals(keyValue[0])) {
                    String userJson = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    String idStr = userJson.split("\"id\":")[1].split(",")[0];
                    Long telegramId = Long.parseLong(idStr);
                    log.debug("Extracted Telegram ID from initData: {}", telegramId);
                    return telegramId;
                }
            }
            log.warn("No user data found in initData");
            return null;
        } catch (Exception e) {
            log.error("Error extracting Telegram ID from initData", e);
            return null;
        }
    }

    private byte[] hmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        return sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
