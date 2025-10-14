package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserRole;
import com.mkisten.subscriptionbackend.service.TelegramAuthService;
import com.mkisten.subscriptionbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Admin", description = "API для административного управления пользователями и подписками")
@SecurityRequirement(name = "JWT")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final TelegramAuthService telegramAuthService;

    // ========== УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ ==========

    @Operation(
            summary = "Получить всех пользователей",
            description = "Возвращает список всех зарегистрированных пользователей"
    )
    @GetMapping("/all-users")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> allUsers = userService.getAllUsers();
            List<AdminUserResponse> responses = allUsers.stream()
                    .map(this::convertToAdminUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new UserListResponse(responses, responses.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("FETCH_FAILED", e.getMessage()));
        }
    }

    @Operation(
            summary = "Получить информацию о пользователе",
            description = "Возвращает подробную информацию о пользователе по Telegram ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Информация о пользователе получена"),
            @ApiResponse(responseCode = "400", description = "Пользователь не найден")
    })
    @GetMapping("/user/{telegramId}")
    public ResponseEntity<?> getUserInfo(
            @Parameter(description = "Telegram ID пользователя", required = true)
            @PathVariable Long telegramId) {
        try {
            User user = userService.findByTelegramId(telegramId);
            return ResponseEntity.ok(convertToAdminUserResponse(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("USER_NOT_FOUND", "User not found with Telegram ID: " + telegramId)
            );
        }
    }

    @Operation(
            summary = "Поиск пользователей",
            description = "Поиск пользователей по имени, фамилии, username или email"
    )
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @Parameter(description = "Поисковый запрос", required = true)
            @RequestParam String query) {
        try {
            List<User> users = userService.searchUsers(query);
            List<AdminUserResponse> responses = users.stream()
                    .map(this::convertToAdminUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new UserListResponse(responses, responses.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("SEARCH_FAILED", e.getMessage()));
        }
    }

    @Operation(summary = "Назначить роль пользователю")
    @PostMapping("/users/{telegramId}/role")
    public ResponseEntity<?> setUserRole(
            @Parameter(description = "Telegram ID пользователя", required = true)
            @PathVariable Long telegramId,
            @Parameter(description = "Новая роль пользователя", required = true)
            @RequestParam UserRole role) {
        try {
            User user = userService.setUserRole(telegramId, role);
            return ResponseEntity.ok(Map.of(
                    "message", "Role updated successfully",
                    "telegramId", user.getTelegramId(),
                    "role", user.getRole()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ROLE_UPDATE_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Получить всех администраторов")
    @GetMapping("/admins")
    public ResponseEntity<?> getAllAdmins() {
        try {
            var admins = userService.findByRole(UserRole.ADMIN);
            List<AdminUserResponse> responses = admins.stream()
                    .map(this::convertToAdminUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new UserListResponse(responses, responses.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "FETCH_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    // ========== УПРАВЛЕНИЕ ПОДПИСКАМИ ==========

    @Operation(
            summary = "Продлить подписку пользователя",
            description = "Продлевает подписку пользователя на указанное количество дней"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Подписка успешно продлена",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка при продлении подписки",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/extend-subscription")
    public ResponseEntity<?> extendSubscription(
            @Parameter(description = "Данные для продления подписки", required = true)
            @RequestBody ExtendSubscriptionRequest request) {
        try {
            User user = userService.extendSubscription(
                    request.telegramId(),
                    request.days(),
                    request.plan()
            );

            return ResponseEntity.ok(convertToAdminUserResponse(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("EXTEND_FAILED", e.getMessage()));
        }
    }

    @Operation(
            summary = "Отменить подписку пользователя",
            description = "Отменяет активную подписку пользователя"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Подписка успешно отменена"),
            @ApiResponse(responseCode = "400", description = "Ошибка при отмене подписки")
    })
    @PostMapping("/cancel-subscription")
    public ResponseEntity<?> cancelSubscription(
            @Parameter(description = "Данные для отмены подписки", required = true)
            @RequestBody CancelSubscriptionRequest request) {
        try {
            User user = userService.cancelSubscription(request.telegramId());
            return ResponseEntity.ok(new MessageResponse(
                    "Subscription cancelled for: " +
                            (user.getUsername() != null ? "@" + user.getUsername() :
                                    user.getFirstName() != null ? user.getFirstName() :
                                            "Telegram ID: " + user.getTelegramId())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("CANCEL_FAILED", e.getMessage()));
        }
    }

    @Operation(
            summary = "Получить активные подписки",
            description = "Возвращает список всех пользователей с активными подписками"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список активных подписок получен"),
            @ApiResponse(responseCode = "400", description = "Ошибка при получении списка")
    })
    @GetMapping("/active-subscriptions")
    public ResponseEntity<?> getActiveSubscriptions() {
        try {
            List<User> activeUsers = userService.getActiveSubscriptions();
            List<AdminUserResponse> responses = activeUsers.stream()
                    .map(this::convertToAdminUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new UserListResponse(responses, responses.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("FETCH_FAILED", e.getMessage()));
        }
    }

    @Operation(
            summary = "Получить истекшие подписки",
            description = "Возвращает список пользователей с истекшими подписками"
    )
    @GetMapping("/expired-subscriptions")
    public ResponseEntity<?> getExpiredSubscriptions() {
        try {
            List<User> expiredUsers = userService.getExpiredSubscriptions();
            List<AdminUserResponse> responses = expiredUsers.stream()
                    .map(this::convertToAdminUserResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new UserListResponse(responses, responses.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("FETCH_FAILED", e.getMessage()));
        }
    }

    // ========== СТАТИСТИКА ==========

    @Operation(
            summary = "Получить статистику",
            description = "Возвращает статистику по пользователям и подпискам"
    )
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            List<User> allUsers = userService.getAllUsers();
            List<User> activeUsers = userService.getActiveSubscriptions();
            List<User> expiredUsers = userService.getExpiredSubscriptions();

            AdminStatsResponse stats = new AdminStatsResponse(
                    allUsers.size(),
                    activeUsers.size(),
                    expiredUsers.size(),
                    allUsers.stream().filter(User::getTrialUsed).count(),
                    allUsers.stream().filter(user -> !user.getTrialUsed()).count()
            );

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("STATS_FAILED", e.getMessage()));
        }
    }

    @Operation(summary = "Проверить права администратора")
    @GetMapping("/check-access")
    public ResponseEntity<?> checkAdminAccess() {
        return ResponseEntity.ok(Map.of(
                "message", "You have admin access",
                "status", "OK"
        ));
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private AdminUserResponse convertToAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getTelegramId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getSubscriptionEndDate(),
                user.getSubscriptionPlan(),
                telegramAuthService.isSubscriptionActive(user),
                telegramAuthService.getDaysRemaining(user),
                user.getTrialUsed(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    // ========== DTO RECORDS ==========

    @Schema(description = "Ответ об ошибке")
    public record ErrorResponse(
            @Schema(description = "Код ошибки") String code,
            @Schema(description = "Сообщение об ошибке") String message) {}

    @Schema(description = "Сообщение ответа")
    public record MessageResponse(@Schema(description = "Текст сообщения") String message) {}

    // Request DTOs
    @Schema(description = "Запрос на продление подписки")
    public record ExtendSubscriptionRequest(
            @Schema(description = "Telegram ID пользователя", required = true) Long telegramId,
            @Schema(description = "Количество дней для продления", required = true) int days,
            @Schema(description = "Тип подписки", required = true) SubscriptionPlan plan) {}

    @Schema(description = "Запрос на отмену подписки")
    public record CancelSubscriptionRequest(
            @Schema(description = "Telegram ID пользователя", required = true) Long telegramId) {}

    // Response DTOs
    @Schema(description = "Ответ с информацией о пользователе для администратора")
    public record AdminUserResponse(
            @Schema(description = "Telegram ID") Long telegramId,
            @Schema(description = "Имя") String firstName,
            @Schema(description = "Фамилия") String lastName,
            @Schema(description = "Username") String username,
            @Schema(description = "Email") String email,
            @Schema(description = "Телефон") String phone,
            @Schema(description = "Дата окончания подписки") java.time.LocalDate subscriptionEndDate,
            @Schema(description = "Тип подписки") SubscriptionPlan subscriptionPlan,
            @Schema(description = "Активна ли подписка") Boolean isActive,
            @Schema(description = "Осталось дней подписки") Integer daysRemaining,
            @Schema(description = "Использован ли trial") Boolean trialUsed,
            @Schema(description = "Роль пользователя") String role,
            @Schema(description = "Дата создания") java.time.LocalDateTime createdAt,
            @Schema(description = "Дата последнего входа") java.time.LocalDateTime lastLoginAt
    ) {}

    @Schema(description = "Ответ со списком пользователей")
    public record UserListResponse(
            @Schema(description = "Список пользователей") List<AdminUserResponse> users,
            @Schema(description = "Общее количество") int totalCount) {}

    @Schema(description = "Статистика администратора")
    public record AdminStatsResponse(
            @Schema(description = "Всего пользователей") int totalUsers,
            @Schema(description = "Активных подписок") int activeSubscriptions,
            @Schema(description = "Истекших подписок") int expiredSubscriptions,
            @Schema(description = "Использовано trial") long trialUsedCount,
            @Schema(description = "Доступно trial") long trialAvailableCount
    ) {}
}