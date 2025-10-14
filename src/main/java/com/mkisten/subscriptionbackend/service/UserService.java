package com.mkisten.subscriptionbackend.service;

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

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhone(request.phone());
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