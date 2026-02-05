package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscription.contract.dto.subscription.SubscriptionStatusDto;
import com.mkisten.subscription.contract.enums.ServiceCodeDto;
import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import com.mkisten.subscriptionbackend.service.SubscriptionStatusService;
import com.mkisten.subscriptionbackend.service.TelegramAuthService;
import com.mkisten.subscriptionbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Subscription", description = "Управление подпиской и проверка статуса")
@SecurityRequirement(name = "bearerAuth")
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
    public ResponseEntity<SubscriptionStatusDto> getSubscriptionStatus(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) ServiceCode service) {
        if (user == null) {
            // здесь можно кинуть InvalidTokenException/SubscriptionExpiredException
            // и отдать ApiErrorDto через GlobalExceptionHandler
            return ResponseEntity.status(401).build();
        }

        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(user, serviceCode);

        log.info("=== SUBSCRIPTION STATUS CHECK ===");
        log.info("User: {} {}", user.getFirstName(), user.getLastName());
        log.info("Telegram ID: {}", user.getTelegramId());
        log.info("Service: {}", serviceCode);
        log.info("Plan: {}", subscription.getSubscriptionPlan());
        log.info("End Date: {}", subscription.getSubscriptionEndDate());
        log.info("Today: {}", LocalDate.now());
        log.info("Trial Used: {}", subscription.getTrialUsed());
        log.info("DB Active Flag: {}", subscription.isActive());

        boolean isActive = telegramAuthService.isSubscriptionActive(subscription);
        long daysRemaining = telegramAuthService.getDaysRemaining(subscription);

        log.info("=== CHECK RESULT ===");
        log.info("Active: {}", isActive);
        log.info("Days Remaining: {}", daysRemaining);

        SubscriptionStatusDto dto = new SubscriptionStatusDto();
        dto.setTelegramId(user.getTelegramId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setSubscriptionEndDate(subscription.getSubscriptionEndDate());
        // если в контракте SubscriptionPlanDto — маппишь через valueOf
        dto.setSubscriptionPlan(
                subscription.getSubscriptionPlan() != null
                        ? SubscriptionPlanDto.valueOf(subscription.getSubscriptionPlan().name())
                        : null
        );
        dto.setActive(isActive);
        dto.setDaysRemaining(daysRemaining);
        dto.setTrialUsed(subscription.getTrialUsed());
        dto.setRole(user.getRole().name());
        dto.setServiceCode(ServiceCodeDto.valueOf(serviceCode.name()));

        return ResponseEntity.ok(dto);
    }

    @Operation(
            summary = "Получить мой статус подписки",
            description = "Альтернативный метод для получения статуса подписки текущего пользователя"
    )
    @GetMapping("/my-status")
    public ResponseEntity<SubscriptionStatusDto> getMySubscriptionStatus(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) ServiceCode service) {
        return getSubscriptionStatus(user, service);
    }

    @Operation(
            summary = "Проверить активность подписки",
            description = "Проверяет активна ли подписка и возвращает оставшееся количество дней"
    )
    @GetMapping("/check-active")
    public ResponseEntity<SubscriptionCheckResponse> checkSubscriptionActive(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) ServiceCode service) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(user, serviceCode);
        boolean isActive = telegramAuthService.isSubscriptionActive(subscription);
        long daysRemaining = telegramAuthService.getDaysRemaining(subscription);

        return ResponseEntity.ok(new SubscriptionCheckResponse(
                isActive,
                daysRemaining,
                subscription.getSubscriptionEndDate(),
                subscription.getSubscriptionPlan()
        ));
    }

    @Operation(
            summary = "Обновить статус подписки",
            description = "Принудительно обновляет статус подписки текущего пользователя"
    )
    @PostMapping("/refresh-status")
    public ResponseEntity<Map<String, Object>> refreshSubscriptionStatus(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) ServiceCode service) {
        if (user == null) {
            return ResponseEntity.status(401).body(
                    Map.of("error", "USER_NOT_AUTHENTICATED", "message", "User not authenticated")
            );
        }

        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(user, serviceCode);
        boolean wasActive = subscription.isActive();
        subscriptionStatusService.updateUserSubscriptionStatus(subscription);

        UserServiceSubscription updatedSubscription = userService.getOrCreateService(user, serviceCode);
        boolean isNowActive = telegramAuthService.isSubscriptionActive(updatedSubscription);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Subscription status refreshed");
        response.put("wasActive", wasActive);
        response.put("isNowActive", isNowActive);
        response.put("subscriptionEndDate", updatedSubscription.getSubscriptionEndDate());
        response.put("service", serviceCode.name());
        response.put("today", LocalDate.now());
        response.put("statusChanged", wasActive != isNowActive);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Диагностика статуса подписки",
            description = "Возвращает детальную диагностическую информацию о статусе подписки"
    )
    @GetMapping("/debug/detailed-status")
    public ResponseEntity<Map<String, Object>> getDetailedSubscriptionStatus(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) ServiceCode service) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(user, serviceCode);

        LocalDate today = LocalDate.now();
        LocalDate endDate = subscription.getSubscriptionEndDate();

        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("telegramId", user.getTelegramId());
        debugInfo.put("firstName", user.getFirstName());
        debugInfo.put("service", serviceCode.name());
        debugInfo.put("subscriptionPlan", subscription.getSubscriptionPlan());
        debugInfo.put("subscriptionEndDate", endDate);
        debugInfo.put("trialUsed", subscription.getTrialUsed());
        debugInfo.put("dbActiveFlag", subscription.isActive());
        debugInfo.put("currentDate", today);
        debugInfo.put("isEndDateAfterToday", endDate != null && endDate.isAfter(today));
        debugInfo.put("isEndDateEqualToday", endDate != null && endDate.isEqual(today));
        debugInfo.put("isEndDateBeforeToday", endDate != null && endDate.isBefore(today));
        debugInfo.put("calculatedActive", telegramAuthService.calculateSubscriptionActive(subscription));
        debugInfo.put("daysBetween", endDate != null
                ? java.time.temporal.ChronoUnit.DAYS.between(today, endDate)
                : -1);

        boolean isActive = telegramAuthService.isSubscriptionActive(subscription);
        debugInfo.put("serviceResult", isActive);

        return ResponseEntity.ok(debugInfo);
    }

    @Schema(description = "Ответ о проверке подписки")
    public record SubscriptionCheckResponse(
            @Schema(description = "Активна ли подписка") boolean isActive,
            @Schema(description = "Осталось дней подписки") long daysRemaining,
            @Schema(description = "Дата окончания подписки") LocalDate subscriptionEndDate,
            @Schema(description = "Тип подписки") SubscriptionPlan subscriptionPlan
    ) {}
}
