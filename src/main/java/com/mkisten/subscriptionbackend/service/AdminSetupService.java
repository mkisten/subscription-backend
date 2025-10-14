package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.UserRole;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminSetupService {

    private final UserService userService;

    @PostConstruct
    public void setupDefaultAdmin() {
        try {
            // Назначьте администратором нужного пользователя по telegramId
            Long adminTelegramId = 6927880904L; // Замените на реальный ID
            userService.setUserRole(adminTelegramId, UserRole.ADMIN);
            System.out.println("Admin user setup completed");
        } catch (Exception e) {
            System.out.println("Admin setup failed: " + e.getMessage());
        }
    }
}