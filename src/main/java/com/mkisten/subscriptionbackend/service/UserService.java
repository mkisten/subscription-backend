package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.controller.AdminController;
import com.mkisten.subscriptionbackend.controller.AuthController;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserRole;
import com.mkisten.subscriptionbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserRepository userRepository;
    private final SubscriptionCalculator subscriptionCalculator;


    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found with Telegram ID: " + telegramId));
    }

    public Optional<User> findByTelegramIdOptional(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public User save(User user) {
        User savedUser = userRepository.save(user);
        log.debug("Saved user: {}", savedUser.getTelegramId());
        return savedUser;
    }

    /**
     * Удаляет пользователя по Telegram ID
     */
    @Transactional
    public void deleteUser(Long telegramId) {
        User user = findByTelegramId(telegramId);
        userRepository.delete(user);
        log.info("User deleted: {}", telegramId);
    }

    /**
     * Обновляет профиль пользователя с проверкой уникальности email
     */
    @Transactional
    public User updateUserProfile(Long telegramId, AdminController.UpdateUserRequest request) {
        User user = findByTelegramId(telegramId);

        // Проверяем уникальность email, если он изменяется
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            Optional<User> existingUserWithEmail = userRepository.findByEmail(request.email());
            if (existingUserWithEmail.isPresent() && !existingUserWithEmail.get().getTelegramId().equals(telegramId)) {
                throw new RuntimeException("Email уже используется другим пользователем: " + request.email());
            }
        }

        // Проверяем уникальность username, если он изменяется
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
        user.setSubscriptionPlan(SubscriptionPlan.TRIAL);
        user.setSubscriptionEndDate(LocalDate.now().plusDays(7));
        user.setTrialUsed(false);

        boolean isActive = subscriptionCalculator.calculateSubscriptionActive(user);
        user.setActive(isActive);

        User savedUser = userRepository.save(user);
        log.info("Created new user: {} {} (ID: {})", firstName, lastName, telegramId);
        return savedUser;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    public User extendSubscription(Long telegramId, int days, SubscriptionPlan plan) {
        User user = findByTelegramId(telegramId);
        LocalDate newEndDate = calculateNewEndDate(user, days);
        user.setSubscriptionEndDate(newEndDate);
        user.setSubscriptionPlan(plan);

        boolean isActive = subscriptionCalculator.calculateSubscriptionActive(user);
        user.setActive(isActive);

        User savedUser = userRepository.save(user);
        log.info("Extended subscription for user {}: +{} days, plan: {}, new end date: {}, active: {}",
                telegramId, days, plan, newEndDate, isActive);
        return savedUser;
    }

    /**
     * Проверяет активна ли подписка пользователя
     */
    public boolean isSubscriptionActive(User user) {
        if (user == null) {
            log.warn("User is null in isSubscriptionActive check");
            return false;
        }
        boolean calculatedActive = subscriptionCalculator.calculateSubscriptionActive(user);
        log.debug("Subscription status for user {}: calculated={}, endDate={}, today={}",
                user.getTelegramId(), calculatedActive,
                user.getSubscriptionEndDate(), LocalDate.now());
        return calculatedActive;
    }

    /**
     * Получает количество оставшихся дней подписки
     */
    public int getDaysRemaining(User user) {
        if (user == null || user.getSubscriptionEndDate() == null) {
            return 0;
        }

        LocalDate endDate = user.getSubscriptionEndDate();
        LocalDate today = LocalDate.now();

        if (today.isAfter(endDate)) {
            return 0;
        }

        long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
        return Math.max(0, (int) daysRemaining);
    }


    private LocalDate calculateNewEndDate(User user, int days) {
        LocalDate currentEndDate = user.getSubscriptionEndDate();
        LocalDate today = LocalDate.now();

        // Если текущая подписка еще активна, продлеваем от конца
        if (currentEndDate != null && !today.isAfter(currentEndDate)) {
            return currentEndDate.plusDays(days);
        } else {
            // Иначе начинаем с сегодняшнего дня
            return today.plusDays(days);
        }
    }

    /**
     * Обновляет флаг активности подписки
     */
    public void updateSubscriptionActiveFlag(Long telegramId, boolean isActive) {
        try {
            User user = findByTelegramId(telegramId);
            if (user.isActive() != isActive) {
                user.setActive(isActive);
                userRepository.save(user);
                log.debug("Updated subscription active flag for user {}: {}", telegramId, isActive);
            }
        } catch (Exception e) {
            log.error("Error updating subscription active flag for user {}", telegramId, e);
        }
    }

    public User cancelSubscription(Long telegramId) {
        User user = findByTelegramId(telegramId);
        user.setSubscriptionEndDate(LocalDate.now().minusDays(1));
        user.setActive(false);
        return userRepository.save(user);
    }

    public List<User> getExpiredSubscriptions() {
        return userRepository.findBySubscriptionEndDateBefore(LocalDate.now());
    }

    public List<User> getActiveSubscriptions() {
        return userRepository.findBySubscriptionEndDateAfter(LocalDate.now());
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
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

    /**
     * Обновляет время последнего входа
     */
    public void updateLastLogin(Long telegramId) {
        try {
            User user = findByTelegramId(telegramId);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Error updating last login for user {}", telegramId, e);
        }
    }
}
