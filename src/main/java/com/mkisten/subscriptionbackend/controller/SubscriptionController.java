package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.SubscriptionStatusResponse;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.SubscriptionStatusService;
import com.mkisten.subscriptionbackend.service.TelegramAuthService;
import com.mkisten.subscriptionbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "API для управления подписками")
@SecurityRequirement(name = "JWT")
public class SubscriptionController {

    private final UserService userService;
    private final TelegramAuthService telegramAuthService;
    private final SubscriptionStatusService subscriptionStatusService;

    @Operation(
            summary = "Получить статус подписки",
            description = "Возвращает подробный статус подписки текущего пользователя"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус подписки получен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован")
    })
    @GetMapping("/status")
    public ResponseEntity<?> getSubscriptionStatus(
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(
                        Map.of("error", "USER_NOT_AUTHENTICATED", "message", "User not authenticated")
                );
            }

            // Детальное логирование для отладки
            log.info("=== SUBSCRIPTION STATUS CHECK ===");
            log.info("User: {} {}", user.getFirstName(), user.getLastName());
            log.info("Telegram ID: {}", user.getTelegramId());
            log.info("Plan: {}", user.getSubscriptionPlan());
            log.info("End Date: {}", user.getSubscriptionEndDate());
            log.info("Today: {}", LocalDate.now());
            log.info("Trial Used: {}", user.getTrialUsed());
            log.info("DB Active Flag: {}", user.isActive());

            boolean isActive = telegramAuthService.isSubscriptionActive(user);
            long daysRemaining = telegramAuthService.getDaysRemaining(user);

            log.info("=== CHECK RESULT ===");
            log.info("Active: {}", isActive);
            log.info("Days Remaining: {}", daysRemaining);

            SubscriptionStatusResponse response = new SubscriptionStatusResponse(
                    user.getTelegramId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getSubscriptionEndDate(),
                    user.getSubscriptionPlan(),
                    isActive,
                    daysRemaining,
                    user.getTrialUsed(),
                    user.getRole().name()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting subscription status", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", "INTERNAL_ERROR", "message", "Failed to get subscription status: " + e.getMessage())
            );
        }
    }

    @Operation(
            summary = "Получить мой статус подписки",
            description = "Альтернативный метод для получения статуса подписки текущего пользователя"
    )
    @GetMapping("/my-status")
    public ResponseEntity<?> getMySubscriptionStatus(
            @AuthenticationPrincipal User user) {
        return getSubscriptionStatus(user);
    }

    @Operation(
            summary = "Проверить активность подписки",
            description = "Проверяет активна ли подписка и возвращает оставшееся количество дней"
    )
    @GetMapping("/check-active")
    public ResponseEntity<?> checkSubscriptionActive(
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(
                        Map.of("error", "USER_NOT_AUTHENTICATED", "message", "User not authenticated")
                );
            }

            boolean isActive = telegramAuthService.isSubscriptionActive(user);
            long daysRemaining = telegramAuthService.getDaysRemaining(user);

            return ResponseEntity.ok(new SubscriptionCheckResponse(
                    isActive,
                    daysRemaining,
                    user.getSubscriptionEndDate(),
                    user.getSubscriptionPlan()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "INTERNAL_ERROR", "message", "Failed to check subscription")
            );
        }
    }

    @Operation(
            summary = "Обновить статус подписки",
            description = "Принудительно обновляет статус подписки текущего пользователя"
    )
    @PostMapping("/refresh-status")
    public ResponseEntity<?> refreshSubscriptionStatus(@AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(
                        Map.of("error", "USER_NOT_AUTHENTICATED", "message", "User not authenticated")
                );
            }

            boolean wasActive = user.isActive();
            subscriptionStatusService.updateUserSubscriptionStatus(user.getTelegramId());

            // Получаем обновленного пользователя
            User updatedUser = userService.findByTelegramId(user.getTelegramId());
            boolean isNowActive = telegramAuthService.isSubscriptionActive(updatedUser);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Subscription status refreshed");
            response.put("wasActive", wasActive);
            response.put("isNowActive", isNowActive);
            response.put("subscriptionEndDate", updatedUser.getSubscriptionEndDate());
            response.put("today", LocalDate.now());

            if (wasActive != isNowActive) {
                response.put("statusChanged", true);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error refreshing subscription status", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to refresh subscription status")
            );
        }
    }

    @Operation(
            summary = "Диагностика статуса подписки",
            description = "Возвращает детальную диагностическую информацию о статусе подписки"
    )
    @GetMapping("/debug/detailed-status")
    public ResponseEntity<?> getDetailedSubscriptionStatus(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        LocalDate today = LocalDate.now();
        LocalDate endDate = user.getSubscriptionEndDate();

        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("telegramId", user.getTelegramId());
        debugInfo.put("firstName", user.getFirstName());
        debugInfo.put("subscriptionPlan", user.getSubscriptionPlan());
        debugInfo.put("subscriptionEndDate", endDate);
        debugInfo.put("trialUsed", user.getTrialUsed());
        debugInfo.put("dbActiveFlag", user.isActive());
        debugInfo.put("currentDate", today);
        debugInfo.put("isEndDateAfterToday", endDate != null && endDate.isAfter(today));
        debugInfo.put("isEndDateEqualToday", endDate != null && endDate.isEqual(today));
        debugInfo.put("isEndDateBeforeToday", endDate != null && endDate.isBefore(today));
        debugInfo.put("calculatedActive", telegramAuthService.calculateSubscriptionActive(user));
        debugInfo.put("daysBetween", endDate != null ? java.time.temporal.ChronoUnit.DAYS.between(today, endDate) : -1);

        // Результат проверки
        boolean isActive = telegramAuthService.isSubscriptionActive(user);
        debugInfo.put("serviceResult", isActive);

        return ResponseEntity.ok(debugInfo);
    }

    // Record для простого ответа о проверке подписки
    @Schema(description = "Ответ о проверке подписки")
    public record SubscriptionCheckResponse(
            @Schema(description = "Активна ли подписка") boolean isActive,
            @Schema(description = "Осталось дней подписки") long daysRemaining,
            @Schema(description = "Дата окончания подписки") LocalDate subscriptionEndDate,
            @Schema(description = "Тип подписки") SubscriptionPlan subscriptionPlan
    ) {}
}