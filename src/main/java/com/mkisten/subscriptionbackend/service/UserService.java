package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.controller.AdminController;
import com.mkisten.subscriptionbackend.controller.AuthController;
import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserRole;
import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import com.mkisten.subscriptionbackend.repository.UserRepository;
import com.mkisten.subscriptionbackend.repository.UserServiceSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final int DEFAULT_TRIAL_DAYS = 7;

    private final UserRepository userRepository;
    private final UserServiceSubscriptionRepository userServiceRepository;
    private final SubscriptionCalculator subscriptionCalculator;
    private final PasswordEncoder passwordEncoder;

    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found with Telegram ID: " + telegramId));
    }

    public Optional<User> findByTelegramIdOptional(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public Optional<User> findByLogin(String login) {
        if (login == null) {
            return Optional.empty();
        }
        return userRepository.findByLogin(normalizeLogin(login));
    }

    public User save(User user) {
        User savedUser = userRepository.save(user);
        log.debug("Saved user: {}", savedUser.getTelegramId());
        return savedUser;
    }

    @Transactional
    public void deleteUser(Long telegramId) {
        User user = findByTelegramId(telegramId);
        userRepository.delete(user);
        log.info("User deleted: {}", telegramId);
    }

    @Transactional
    public User updateUserProfile(Long telegramId, AdminController.UpdateUserRequest request) {
        User user = findByTelegramId(telegramId);

        if (request.email() != null && !request.email().isBlank() && !request.email().equals(user.getEmail())) {
            Optional<User> existingUserWithEmail = userRepository.findByEmail(request.email());
            if (existingUserWithEmail.isPresent() && !existingUserWithEmail.get().getTelegramId().equals(telegramId)) {
                throw new RuntimeException("Email уже используется другим пользователем: " + request.email());
            }
        }

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            Optional<User> existingUserWithUsername = userRepository.findByUsername(request.username());
            if (existingUserWithUsername.isPresent() && !existingUserWithUsername.get().getTelegramId().equals(telegramId)) {
                throw new RuntimeException("Username уже используется другим пользователем: " + request.username());
            }
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.username() != null) {
            user.setUsername(request.username());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }

        if (request.subscriptionPlan() != null || request.subscriptionDays() != null) {
            ServiceCode serviceCode = request.service() != null ? request.service() : ServiceCode.VACANCY;
            UserServiceSubscription subscription = getOrCreateService(user, serviceCode);

            if (request.subscriptionPlan() != null) {
                subscription.setSubscriptionPlan(request.subscriptionPlan());
                if (request.subscriptionPlan() == SubscriptionPlan.LIFETIME) {
                    subscription.setSubscriptionEndDate(LocalDate.now().plusDays(36500));
                }
            }
            if (request.subscriptionDays() != null) {
                subscription.setSubscriptionEndDate(LocalDate.now().plusDays(request.subscriptionDays()));
            }

            boolean isActive = subscriptionCalculator.calculateSubscriptionActive(subscription);
            subscription.setActive(isActive);
            userServiceRepository.save(subscription);
        }

        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        log.info("User profile updated: {}", telegramId);
        return savedUser;
    }

    public User createUser(Long telegramId, String firstName, String lastName, String username) {
        User user = new User();
        user.setTelegramId(telegramId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);

        User savedUser = userRepository.save(user);
        ensureTrialSubscription(savedUser, ServiceCode.VACANCY);

        log.info("Created new user: {} {} (ID: {})", firstName, lastName, telegramId);
        return savedUser;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    public boolean isLoginAvailable(String login, Long currentTelegramId) {
        if (login == null || login.isBlank()) {
            return false;
        }
        String normalized = normalizeLogin(login);
        Optional<User> existing = userRepository.findByLogin(normalized);
        if (existing.isEmpty()) {
            return true;
        }
        if (currentTelegramId == null) {
            return false;
        }
        return existing.get().getTelegramId().equals(currentTelegramId);
    }

    public User updateCredentials(Long telegramId, String login, String rawPassword) {
        User user = findByTelegramId(telegramId);

        boolean hasLogin = login != null && !login.isBlank();
        boolean hasPassword = rawPassword != null && !rawPassword.isBlank();

        if (!hasLogin && !hasPassword) {
            throw new RuntimeException("Login or password must be provided");
        }

        if (hasLogin) {
            String normalized = normalizeLogin(login);
            validateLogin(normalized);
            if (!isLoginAvailable(normalized, telegramId)) {
                throw new RuntimeException("Login already in use");
            }
            user.setLogin(normalized);
        }

        if (hasPassword) {
            validatePassword(rawPassword);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setPasswordUpdatedAt(LocalDateTime.now());
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User updateLogin(Long telegramId, String login) {
        return updateCredentials(telegramId, login, null);
    }

    public User updatePassword(Long telegramId, String rawPassword) {
        return updateCredentials(telegramId, null, rawPassword);
    }

    public UserServiceSubscription extendSubscription(Long telegramId, int days, SubscriptionPlan plan, ServiceCode serviceCode) {
        User user = findByTelegramId(telegramId);
        UserServiceSubscription subscription = getOrCreateService(user, serviceCode);

        LocalDate newEndDate = calculateNewEndDate(subscription, days);
        subscription.setSubscriptionEndDate(newEndDate);
        subscription.setSubscriptionPlan(plan);
        subscription.setTrialUsed(true);

        boolean isActive = subscriptionCalculator.calculateSubscriptionActive(subscription);
        subscription.setActive(isActive);

        UserServiceSubscription saved = userServiceRepository.save(subscription);
        log.info("Extended subscription for user {}: +{} days, plan: {}, service: {}, new end date: {}, active: {}",
                telegramId, days, plan, serviceCode, newEndDate, isActive);
        return saved;
    }

    public UserServiceSubscription extendSubscription(Long telegramId, int days, SubscriptionPlan plan) {
        return extendSubscription(telegramId, days, plan, ServiceCode.VACANCY);
    }

    public boolean isSubscriptionActive(UserServiceSubscription subscription) {
        if (subscription == null) {
            return false;
        }
        boolean calculatedActive = subscriptionCalculator.calculateSubscriptionActive(subscription);
        log.debug("Subscription status for user {} service {}: calculated={}, endDate={}, today={}",
                subscription.getUser().getTelegramId(), subscription.getServiceCode(), calculatedActive,
                subscription.getSubscriptionEndDate(), LocalDate.now());
        return calculatedActive;
    }

    public int getDaysRemaining(UserServiceSubscription subscription) {
        if (subscription == null || subscription.getSubscriptionEndDate() == null) {
            return 0;
        }
        LocalDate endDate = subscription.getSubscriptionEndDate();
        LocalDate today = LocalDate.now();

        if (today.isAfter(endDate)) {
            return 0;
        }

        long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
        return Math.max(0, (int) daysRemaining);
    }

    private LocalDate calculateNewEndDate(UserServiceSubscription subscription, int days) {
        LocalDate currentEndDate = subscription.getSubscriptionEndDate();
        LocalDate today = LocalDate.now();

        if (currentEndDate != null && !today.isAfter(currentEndDate)) {
            return currentEndDate.plusDays(days);
        } else {
            return today.plusDays(days);
        }
    }

    public UserServiceSubscription cancelSubscription(Long telegramId, ServiceCode serviceCode) {
        User user = findByTelegramId(telegramId);
        UserServiceSubscription subscription = getOrCreateService(user, serviceCode);
        subscription.setSubscriptionEndDate(LocalDate.now().minusDays(1));
        subscription.setActive(false);
        return userServiceRepository.save(subscription);
    }

    public UserServiceSubscription cancelSubscription(Long telegramId) {
        return cancelSubscription(telegramId, ServiceCode.VACANCY);
    }

    public List<UserServiceSubscription> getExpiredSubscriptions(ServiceCode serviceCode) {
        return userServiceRepository.findByServiceCodeAndSubscriptionEndDateBefore(serviceCode, LocalDate.now());
    }

    public List<UserServiceSubscription> getActiveSubscriptions(ServiceCode serviceCode) {
        return userServiceRepository.findByServiceCodeAndSubscriptionEndDateAfter(serviceCode, LocalDate.now());
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<UserServiceSubscription> getAllUserServices(ServiceCode serviceCode) {
        return userServiceRepository.findByServiceCode(serviceCode);
    }

    public List<UserServiceSubscription> getAllUserServices() {
        return userServiceRepository.findAll();
    }

    public List<User> searchUsers(String query) {
        return userRepository.searchUsers(query);
    }

    public User updateUserProfile(Long telegramId, AuthController.ProfileUpdateRequest request) {
        User user = findByTelegramId(telegramId);

        if (request == null) {
            return user;
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            Optional<User> existingUserWithEmail = userRepository.findByEmail(request.email());
            if (existingUserWithEmail.isPresent()
                    && !existingUserWithEmail.get().getTelegramId().equals(telegramId)) {
                throw new RuntimeException("Email уже используется другим пользователем: " + request.email());
            }
        }

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            Optional<User> existingUserWithUsername = userRepository.findByUsername(request.username());
            if (existingUserWithUsername.isPresent()
                    && !existingUserWithUsername.get().getTelegramId().equals(telegramId)) {
                throw new RuntimeException("Username уже используется другим пользователем: " + request.username());
            }
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.username() != null) {
            user.setUsername(request.username());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    public User setUserRole(Long telegramId, UserRole role) {
        User user = findByTelegramId(telegramId);
        user.setRole(role);
        return userRepository.save(user);
    }

    public boolean isAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    public List<User> findByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    public void updateLastLogin(Long telegramId) {
        try {
            User user = findByTelegramId(telegramId);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Error updating last login for user {}", telegramId, e);
        }
    }

    public void updateServiceLastLogin(Long telegramId, ServiceCode serviceCode) {
        try {
            User user = findByTelegramId(telegramId);
            UserServiceSubscription subscription = getOrCreateService(user, serviceCode);
            subscription.setLastLoginAt(LocalDateTime.now());
            userServiceRepository.save(subscription);
        } catch (Exception e) {
            log.error("Error updating service login for user {} service {}", telegramId, serviceCode, e);
        }
    }

    public UserServiceSubscription getOrCreateService(User user, ServiceCode serviceCode) {
        return userServiceRepository.findByUserAndServiceCode(user, serviceCode)
                .orElseGet(() -> createServiceSubscription(user, serviceCode));
    }

    public UserServiceSubscription createServiceSubscription(User user, ServiceCode serviceCode) {
        UserServiceSubscription subscription = new UserServiceSubscription();
        subscription.setUser(user);
        subscription.setServiceCode(serviceCode);
        subscription.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        subscription.setSubscriptionEndDate(LocalDate.now().plusDays(DEFAULT_TRIAL_DAYS));
        subscription.setTrialUsed(true);
        subscription.setActive(subscriptionCalculator.calculateSubscriptionActive(subscription));
        return userServiceRepository.save(subscription);
    }

    public UserServiceSubscription ensureTrialSubscription(User user, ServiceCode serviceCode) {
        UserServiceSubscription subscription = getOrCreateService(user, serviceCode);
        if (subscription.getSubscriptionEndDate() == null) {
            subscription.setSubscriptionEndDate(LocalDate.now().plusDays(DEFAULT_TRIAL_DAYS));
        }
        subscription.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        subscription.setTrialUsed(true);
        subscription.setActive(subscriptionCalculator.calculateSubscriptionActive(subscription));
        return userServiceRepository.save(subscription);
    }

    public Optional<UserServiceSubscription> findUserService(User user, ServiceCode serviceCode) {
        return userServiceRepository.findByUserAndServiceCode(user, serviceCode);
    }

    public List<UserServiceSubscription> getUserServices(User user) {
        return userServiceRepository.findByUser(user);
    }

    private String normalizeLogin(String login) {
        return login.trim().toLowerCase();
    }

    private void validateLogin(String login) {
        if (login.length() < 3 || login.length() > 32) {
            throw new RuntimeException("Login must be 3-32 characters");
        }
        if (!login.matches("^[a-z0-9._-]+$")) {
            throw new RuntimeException("Login may contain только латинские буквы, цифры, точку, дефис и подчёркивание");
        }
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword.length() < 6 || rawPassword.length() > 128) {
            throw new RuntimeException("Password must be 6-128 characters");
        }
    }
}
