package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.*;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.security.JwtUtil;
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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "API для аутентификации и управления профилем")
@SecurityRequirement(name = "JWT")
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Operation(
            summary = "Получить текущего пользователя",
            description = "Возвращает информацию о текущем аутентифицированном пользователе"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Информация о пользователе получена"),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован")
    })
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        try {
            // Обновляем время последнего входа
            userService.updateLastLogin(user.getTelegramId());

            AuthResponse response = new AuthResponse(
                    null,
                    user.getTelegramId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getSubscriptionEndDate(),
                    telegramAuthService.isSubscriptionActive(user),
                    user.getSubscriptionPlan(),
                    user.getTrialUsed(),
                    telegramAuthService.getDaysRemaining(user),
                    user.getRole().name()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                    new ErrorResponse("USER_NOT_FOUND", e.getMessage())
            );
        }
    }

    @Operation(
            summary = "Проверить статус подписки",
            description = "Проверяет статус подписки текущего пользователя"
    )
    @GetMapping("/check-subscription")
    public ResponseEntity<?> checkSubscription(
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        try {
            SubscriptionStatusResponse response = new SubscriptionStatusResponse(
                    user.getTelegramId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getSubscriptionEndDate(),
                    user.getSubscriptionPlan(),
                    telegramAuthService.isSubscriptionActive(user),
                    telegramAuthService.getDaysRemaining(user),
                    user.getTrialUsed(),
                    user.getRole().name()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                    new ErrorResponse("SUBSCRIPTION_CHECK_FAILED", e.getMessage())
            );
        }
    }

    @Operation(
            summary = "Обновить профиль",
            description = """
        Обновляет профиль текущего пользователя.
        
        **Обновляемые поля:**
        - firstName (Имя)
        - lastName (Фамилия) 
        - username (Username в Telegram)
        - email (Email)
        - phone (Телефон)
        
        **Примечание:** Все поля опциональны. Можно обновлять как все поля, так и только некоторые.
        Telegram ID не может быть изменен.
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Профиль успешно обновлен",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Ошибка при обновлении профиля",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal User currentUser,
            @Parameter(
                    description = "Данные для обновления профиля",
                    required = true
            )
            @RequestBody ProfileUpdateRequest request) {
        try {
            User user = userService.updateUserProfile(
                    currentUser.getTelegramId(),
                    request
            );

            // Возвращаем полную информацию о пользователе включая роль
            ProfileResponse response = new ProfileResponse(
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
                    user.getRole().name(), // Добавляем роль
                    user.getCreatedAt(),
                    user.getLastLoginAt()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("UPDATE_FAILED", e.getMessage())
            );
        }
    }

    @Operation(
            summary = "Обновить токен",
            description = "Генерирует новый JWT токен для текущего пользователя"
    )
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        try {
            String newToken = jwtUtil.generateToken(user.getTelegramId());
            return ResponseEntity.ok(new TokenResponse(newToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                    new ErrorResponse("TOKEN_REFRESH_FAILED", e.getMessage())
            );
        }
    }

    @Operation(
            summary = "Получить токен по Telegram ID",
            description = "Генерирует JWT токен для пользователя по Telegram ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Токен успешно сгенерирован"),
            @ApiResponse(responseCode = "400", description = "Пользователь не найден")
    })
    @PostMapping("/token")
    public ResponseEntity<?> getToken(
            @Parameter(description = "Telegram ID пользователя", required = true)
            @RequestParam Long telegramId) {
        try {
            User user = userService.findByTelegramId(telegramId);
            String token = jwtUtil.generateToken(user.getTelegramId());

            // Обновляем время последнего входа
            userService.updateLastLogin(telegramId);

            return ResponseEntity.ok(new TokenResponse(token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("USER_NOT_FOUND", "User not registered. Please register via Telegram bot first.")
            );
        }
    }

    @Operation(
            summary = "Проверить валидность токена",
            description = "Проверяет валидность текущего JWT токена"
    )
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(
            @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new MessageResponse("Token is valid"));
    }


    @Schema(description = "Ответ об ошибке")
    public record ErrorResponse(
            @Schema(description = "Код ошибки") String code,
            @Schema(description = "Сообщение об ошибке") String message) {}

    @Schema(description = "Запрос на обновление профиля")
    public record ProfileUpdateRequest(
            @Schema(description = "Имя пользователя", example = "Иван")
            @Size(max = 100, message = "Имя не должно превышать 100 символов")
            String firstName,

            @Schema(description = "Фамилия пользователя", example = "Иванов")
            @Size(max = 100, message = "Фамилия не должна превышать 100 символов")
            String lastName,

            @Schema(description = "Username в Telegram", example = "ivanov")
            @Size(max = 50, message = "Username не должен превышать 50 символов")
            String username,

            @Schema(description = "Email пользователя", example = "user@example.com")
            @Email(message = "Некорректный формат email")
            @Size(max = 255, message = "Email не должен превышать 255 символов")
            String email,

            @Schema(description = "Телефон пользователя", example = "+79123456789")
            @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Некорректный формат телефона")
            @Size(max = 20, message = "Телефон не должен превышать 20 символов")
            String phone
    ) {}

    @Schema(description = "Ответ с токеном")
    public record TokenResponse(@Schema(description = "JWT токен") String token) {}

    @Schema(description = "Сообщение ответа")
    public record MessageResponse(@Schema(description = "Текст сообщения") String message) {}

    // Profile Response DTO
    @Schema(description = "Ответ с информацией о профиле")
    public record ProfileResponse(
            @Schema(description = "Telegram ID") Long telegramId,
            @Schema(description = "Имя") String firstName,
            @Schema(description = "Фамилия") String lastName,
            @Schema(description = "Username") String username,
            @Schema(description = "Email") String email,
            @Schema(description = "Телефон") String phone,
            @Schema(description = "Дата окончания подписки") LocalDate subscriptionEndDate,
            @Schema(description = "Тип подписки") SubscriptionPlan subscriptionPlan,
            @Schema(description = "Активна ли подписка") Boolean isActive,
            @Schema(description = "Осталось дней подписки") Integer daysRemaining,
            @Schema(description = "Использован ли trial") Boolean trialUsed,
            @Schema(description = "Роль пользователя") String role,
            @Schema(description = "Дата создания") LocalDateTime createdAt,
            @Schema(description = "Дата последнего входа") LocalDateTime lastLoginAt
    ) {}
}