package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscription.contract.dto.auth.TokenResponseDto;
import com.mkisten.subscription.contract.dto.auth.TokenValidationResponseDto;
import com.mkisten.subscription.contract.dto.subscription.SubscriptionStatusDto;
import com.mkisten.subscription.contract.dto.user.UserProfileDto;
import com.mkisten.subscription.contract.enums.ServiceCodeDto;
import com.mkisten.subscription.contract.enums.SubscriptionPlanDto;
import com.mkisten.subscriptionbackend.dto.CredentialsRequest;
import com.mkisten.subscriptionbackend.dto.LoginAvailabilityResponse;
import com.mkisten.subscriptionbackend.dto.LoginRequest;
import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import com.mkisten.subscriptionbackend.service.SubscriptionStatusService;
import com.mkisten.subscriptionbackend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final SubscriptionStatusService subscriptionStatusService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Выдать JWT по telegramId.
     * Используется vacancy‑сервисом.
     */
    @GetMapping("/token")
    public ResponseEntity<TokenResponseDto> getToken(@RequestParam Long telegramId,
                                                     @RequestParam(required = false) ServiceCode service) {
        User user = userService.findByTelegramIdOptional(telegramId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        userService.getOrCreateService(user, serviceCode);
        String token = jwtUtil.generateToken(user.getTelegramId());

        // Обновляем время последнего входа
        userService.updateLastLogin(telegramId);
        userService.updateServiceLastLogin(telegramId, serviceCode);

        return ResponseEntity.ok(new TokenResponseDto(token));
    }

    /**
     * Вход по логину и паролю.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody LoginRequest request,
                                                  @RequestParam(required = false) ServiceCode service) {
        if (request == null || request.getLogin() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = userService.findByLogin(request.getLogin()).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            return ResponseEntity.status(401).build();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).build();
        }

        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        userService.getOrCreateService(user, serviceCode);
        String token = jwtUtil.generateToken(user.getTelegramId());
        userService.updateLastLogin(user.getTelegramId());
        userService.updateServiceLastLogin(user.getTelegramId(), serviceCode);

        return ResponseEntity.ok(new TokenResponseDto(token));
    }

    /**
     * Проверка доступности логина.
     */
    @GetMapping("/credentials/availability")
    public ResponseEntity<LoginAvailabilityResponse> checkLoginAvailability(@RequestParam String login,
                                                                            @AuthenticationPrincipal UserDetails principal) {
        Long currentTelegramId = null;
        if (principal instanceof User user) {
            currentTelegramId = user.getTelegramId();
        }
        boolean available = userService.isLoginAvailable(login, currentTelegramId);
        String normalized = login == null ? null : login.trim().toLowerCase();
        return ResponseEntity.ok(new LoginAvailabilityResponse(available, normalized));
    }

    /**
     * Установка/обновление логина и/или пароля для текущего пользователя.
     */
    @PutMapping("/credentials")
    public ResponseEntity<UserProfileDto> updateCredentials(@AuthenticationPrincipal UserDetails principal,
                                                            @RequestBody CredentialsRequest request,
                                                            @RequestParam(required = false) ServiceCode service) {
        if (!(principal instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        User updated = userService.updateCredentials(user.getTelegramId(), request.getLogin(), request.getPassword());
        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(updated, serviceCode);
        return ResponseEntity.ok(mapUserToProfileDto(updated, subscription));
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
        return getToken(request.telegramId(), request.service());
    }

    /**
     * Регистрация пользователя по telegramId (для сервисных ботов).
     */
    @PostMapping("/telegram/register")
    public ResponseEntity<TokenResponseDto> registerTelegram(@RequestBody TelegramRegisterRequest request) {
        if (request == null || request.telegramId() == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.findByTelegramIdOptional(request.telegramId())
                .orElseGet(() -> userService.createUser(
                        request.telegramId(),
                        request.firstName(),
                        request.lastName(),
                        request.username()
                ));
        ServiceCode serviceCode = request.service() != null ? request.service() : ServiceCode.VACANCY;
        userService.getOrCreateService(user, serviceCode);

        String token = jwtUtil.generateToken(user.getTelegramId());
        userService.updateLastLogin(user.getTelegramId());
        userService.updateServiceLastLogin(user.getTelegramId(), serviceCode);

        return ResponseEntity.ok(new TokenResponseDto(token));
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
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) ServiceCode service
    ) {
        User user = resolveUser(principal);

        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(user, serviceCode);
        UserProfileDto dto = mapUserToProfileDto(user, subscription);
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
        User user = resolveUser(principal);

        User updated = userService.updateUserProfile(user.getTelegramId(), request);
        UserServiceSubscription subscription = userService.getOrCreateService(updated, ServiceCode.VACANCY);
        return ResponseEntity.ok(mapUserToProfileDto(updated, subscription));
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

    private UserProfileDto mapUserToProfileDto(User user, UserServiceSubscription subscription) {
        boolean isActive = userService.isSubscriptionActive(subscription);
        int daysRemaining = userService.getDaysRemaining(subscription);

        SubscriptionPlanDto planDto = null;
        if (subscription.getSubscriptionPlan() != null) {
            planDto = SubscriptionPlanDto.valueOf(subscription.getSubscriptionPlan().name());
        }

        UserProfileDto dto = new UserProfileDto();
        dto.setTelegramId(user.getTelegramId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setLogin(user.getLogin());

        dto.setSubscriptionEndDate(subscription.getSubscriptionEndDate());
        dto.setSubscriptionPlan(planDto);
        dto.setIsActive(isActive);
        dto.setDaysRemaining(daysRemaining);
        dto.setTrialUsed(subscription.getTrialUsed());
        dto.setServiceCode(ServiceCodeDto.valueOf(subscription.getServiceCode().name()));

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
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) ServiceCode service
    ) {
        User user = resolveUser(principal);
        ServiceCode serviceCode = service != null ? service : ServiceCode.VACANCY;
        UserServiceSubscription subscription = userService.getOrCreateService(user, serviceCode);
        SubscriptionStatusDto dto = mapUserToSubscriptionStatusDto(user, subscription);
        return ResponseEntity.ok(dto);
    }

    private User resolveUser(UserDetails principal) {
        if (principal instanceof User user) {
            return user;
        }
        String username = principal.getUsername();
        return userService.findByUsername(username);
    }

    private SubscriptionStatusDto mapUserToSubscriptionStatusDto(User user, UserServiceSubscription subscription) {
        boolean isActive = userService.isSubscriptionActive(subscription);
        long daysRemaining = userService.getDaysRemaining(subscription);

        SubscriptionPlanDto planDto = null;
        if (subscription.getSubscriptionPlan() != null) {
            planDto = SubscriptionPlanDto.valueOf(subscription.getSubscriptionPlan().name());
        }

        SubscriptionStatusDto dto = new SubscriptionStatusDto();
        dto.setTelegramId(user.getTelegramId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setSubscriptionEndDate(subscription.getSubscriptionEndDate());
        dto.setSubscriptionPlan(planDto);
        dto.setActive(isActive);
        dto.setDaysRemaining((long) daysRemaining);
        dto.setTrialUsed(subscription.getTrialUsed());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        dto.setServiceCode(ServiceCodeDto.valueOf(subscription.getServiceCode().name()));

        return dto;
    }

    public record TokenRequest(Long telegramId, ServiceCode service) {}

    public record TelegramRegisterRequest(
            Long telegramId,
            String firstName,
            String lastName,
            String username,
            ServiceCode service
    ) {}

    public record ProfileUpdateRequest(
            String firstName,
            String lastName,
            String username,
            String email,
            String phone
    ) {}
}
