package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscription.contract.dto.auth.TokenResponseDto;
import com.mkisten.subscription.contract.dto.auth.TokenValidationResponseDto;
import com.mkisten.subscription.contract.dto.subscription.SubscriptionStatusDto;
import com.mkisten.subscription.contract.dto.user.UserProfileDto;
import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import com.mkisten.subscriptionbackend.service.SubscriptionStatusService;
import com.mkisten.subscriptionbackend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final SubscriptionStatusService subscriptionStatusService;

    /**
     * Выдать JWT по telegramId.
     * Используется vacancy‑сервисом.
     */
    @GetMapping("/token")
    public ResponseEntity<TokenResponseDto> getToken(@RequestParam Long telegramId) {
        User user = userService.findByTelegramIdOptional(telegramId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        String token = jwtUtil.generateToken(user.getTelegramId());

        // Обновляем время последнего входа
        userService.updateLastLogin(telegramId);

        return ResponseEntity.ok(new TokenResponseDto(token));
    }

    /**
     * Выдать JWT по telegramId (POST).
     * Для совместимости с клиентами, которые отправляют JSON.
     */
    @PostMapping("/token")
    public ResponseEntity<TokenResponseDto> getTokenPost(@RequestBody TokenRequest request) {
        if (request == null || request.telegramId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return getToken(request.telegramId());
    }

    /**
     * Валидация токена.
     * Для фильтра в vacancy‑сервисе.
     * Если токен невалидный, сюда вообще не дойдет – Spring Security отдаст 401.
     */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponseDto> validateToken(
            @AuthenticationPrincipal UserDetails principal
    ) {
        // если попали сюда – токен прошел все фильтры
        String username = principal.getUsername();
        log.debug("Token validated for user: {}", username);
        return ResponseEntity.ok(new TokenValidationResponseDto(true, "Token is valid"));
    }

    /**
     * Профиль текущего пользователя (по JWT).
     * Используется vacancy‑сервисом.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(
            @AuthenticationPrincipal UserDetails principal
    ) {
        String username = principal.getUsername();
        User user = userService.findByUsername(username);

        UserProfileDto dto = mapUserToProfileDto(user);
        return ResponseEntity.ok(dto);
    }

    /**
     * Обновить профиль текущего пользователя (по JWT).
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateCurrentUserProfile(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody ProfileUpdateRequest request
    ) {
        String username = principal.getUsername();
        User user = userService.findByUsername(username);

        User updated = userService.updateUserProfile(user.getTelegramId(), request);
        return ResponseEntity.ok(mapUserToProfileDto(updated));
    }

    /**
     * Обновить JWT токен.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refreshToken(
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }
        String token = authorization.substring(7);
        String refreshed = jwtUtil.refreshToken(token);
        return ResponseEntity.ok(new TokenResponseDto(refreshed));
    }

    // --- private mapping helpers ---

    private UserProfileDto mapUserToProfileDto(User user) {
        boolean isActive = userService.isSubscriptionActive(user);
        int daysRemaining = userService.getDaysRemaining(user);

        SubscriptionPlanDto planDto = null;
        if (user.getSubscriptionPlan() != null) {
            planDto = SubscriptionPlanDto.valueOf(user.getSubscriptionPlan().name());
        }

        UserProfileDto dto = new UserProfileDto();
        dto.setTelegramId(user.getTelegramId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());

        dto.setSubscriptionEndDate(user.getSubscriptionEndDate());
        dto.setSubscriptionPlan(planDto);
        dto.setIsActive(isActive);
        dto.setDaysRemaining(daysRemaining);
        dto.setTrialUsed(user.getTrialUsed());

        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());

        return dto;
    }

    /**
     * Если хочешь, можно оставить этот метод или перенести в отдельный контроллер.
     * Публичный статус подписки текущего пользователя.
     */
    @GetMapping("/subscription/status")
    public ResponseEntity<SubscriptionStatusDto> getSubscriptionStatus(
            @AuthenticationPrincipal UserDetails principal
    ) {
        String username = principal.getUsername();
        User user = userService.findByUsername(username);
        SubscriptionStatusDto dto = mapUserToSubscriptionStatusDto(user);
        return ResponseEntity.ok(dto);
    }

    private SubscriptionStatusDto mapUserToSubscriptionStatusDto(User user) {
        boolean isActive = userService.isSubscriptionActive(user);
        long daysRemaining = userService.getDaysRemaining(user);

        SubscriptionPlanDto planDto = null;
        if (user.getSubscriptionPlan() != null) {
            planDto = SubscriptionPlanDto.valueOf(user.getSubscriptionPlan().name());
        }

        SubscriptionStatusDto dto = new SubscriptionStatusDto();
        dto.setTelegramId(user.getTelegramId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setSubscriptionEndDate(user.getSubscriptionEndDate());
        dto.setSubscriptionPlan(planDto);
        dto.setActive(isActive);
        dto.setDaysRemaining((long) daysRemaining);
        dto.setTrialUsed(user.getTrialUsed());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);

        return dto;
    }

    public record TokenRequest(Long telegramId) {}

    public record ProfileUpdateRequest(
            String firstName,
            String lastName,
            String username,
            String email,
            String phone
    ) {}
}
