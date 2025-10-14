package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.service.TelegramBotService;
import com.mkisten.subscriptionbackend.service.UserService;
import com.mkisten.subscriptionbackend.service.BotMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/admin/bot")
@RequiredArgsConstructor
@Tag(name = "Bot Management", description = "API для управления Telegram ботом")
@PreAuthorize("hasRole('ADMIN')")
public class BotManagementController {

    private final TelegramBotService telegramBotService;
    private final UserService userService;
    private final BotMessageService botMessageService;

    @Operation(summary = "Получить статистику бота")
    @GetMapping("/stats")
    public ResponseEntity<?> getBotStats() {
        try {
            var allUsers = userService.getAllUsers();
            var activeUsers = userService.getActiveSubscriptions();
            long totalMessages = botMessageService.getTotalMessages();
            long messagesToday = botMessageService.getMessagesToday();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", allUsers.size());
            stats.put("activeToday", activeUsers.size());
            stats.put("totalMessages", totalMessages);
            stats.put("messagesToday", messagesToday);
            stats.put("botStatus", "online");
            stats.put("lastUpdate", LocalDateTime.now());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting bot stats", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get bot stats",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Отправить рассылку всем пользователям")
    @PostMapping("/broadcast")
    public ResponseEntity<?> sendBroadcast(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Message is required"
                ));
            }

            var allUsers = userService.getAllUsers();
            int successCount = 0;
            int failCount = 0;

            // Отправляем сообщения асинхронно
            for (var user : allUsers) {
                try {
                    telegramBotService.sendTextMessageToUser(user.getTelegramId(), message);
                    successCount++;

                    // Небольшая задержка чтобы не спамить Telegram API
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.error("Failed to send broadcast to user {}", user.getTelegramId(), e);
                    failCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("message", "Broadcast completed");
            result.put("totalUsers", allUsers.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("sentAt", LocalDateTime.now());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error sending broadcast", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send broadcast",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Отправить тестовое сообщение")
    @PostMapping("/test-message")
    public ResponseEntity<?> sendTestMessage(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            Long telegramId = request.get("telegramId") != null ?
                    Long.parseLong(request.get("telegramId")) : null;

            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Message is required"
                ));
            }

            // Если telegramId не указан, отправляем себе (админу)
            if (telegramId == null) {
                // Получаем telegramId из конфигурации админа
                String adminChatId = "6927880904"; // из конфигурации
                telegramId = Long.parseLong(adminChatId);
            }

            telegramBotService.sendTextMessageToUser(telegramId, "🧪 **Тестовое сообщение:**\n\n" + message);

            return ResponseEntity.ok(Map.of(
                    "message", "Test message sent",
                    "telegramId", telegramId,
                    "sentAt", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error sending test message", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to send test message",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Управление ботом")
    @PostMapping("/control")
    public ResponseEntity<?> controlBot(@RequestBody Map<String, String> request) {
        try {
            String action = request.get("action");

            switch (action) {
                case "restart":
                    // Здесь может быть перезагрузка конфигурации или другие действия
                    log.info("Bot configuration reload requested");
                    break;
                case "stop":
                    log.warn("Bot stop requested - Telegram bot runs continuously");
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Telegram bot cannot be stopped via API"
                    ));
                case "start":
                    log.info("Bot start requested - already running");
                    break;
                default:
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Unknown action: " + action
                    ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Action processed: " + action,
                    "status", "success",
                    "processedAt", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error controlling bot", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to control bot",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Экспорт пользователей в CSV")
    @GetMapping(value = "/export-users", produces = "text/csv")
    public ResponseEntity<?> exportUsers() {
        try {
            var users = userService.getAllUsers();

            // Генерируем CSV
            StringBuilder csv = new StringBuilder();
            csv.append("Telegram ID,First Name,Last Name,Username,Email,Phone,Subscription End,Plan,Status,Days Remaining,Role,Created At\n");

            for (var user : users) {
                csv.append(user.getTelegramId()).append(",");
                csv.append(escapeCsv(user.getFirstName())).append(",");
                csv.append(escapeCsv(user.getLastName())).append(",");
                csv.append(escapeCsv(user.getUsername())).append(",");
                csv.append(escapeCsv(user.getEmail())).append(",");
                csv.append(escapeCsv(user.getPhone())).append(",");
                csv.append(user.getSubscriptionEndDate()).append(",");
                csv.append(user.getSubscriptionPlan()).append(",");
                csv.append(userService.isSubscriptionActive(user) ? "ACTIVE" : "INACTIVE").append(",");
                csv.append(userService.getDaysRemaining(user)).append(",");
                csv.append(user.getRole()).append(",");
                csv.append(user.getCreatedAt()).append("\n");
            }

            byte[] csvBytes = csv.toString().getBytes();

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=users-export-" + LocalDateTime.now().toLocalDate() + ".csv")
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .body(csvBytes);

        } catch (Exception e) {
            log.error("Error exporting users", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to export users",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Получить детальную статистику бота")
    @GetMapping("/detailed-stats")
    public ResponseEntity<?> getDetailedStats() {
        try {
            var allUsers = userService.getAllUsers();
            var activeUsers = userService.getActiveSubscriptions();
            var expiredUsers = userService.getExpiredSubscriptions();

            Map<String, Object> stats = new HashMap<>();

            // Основная статистика
            stats.put("totalUsers", allUsers.size());
            stats.put("activeSubscriptions", activeUsers.size());
            stats.put("expiredSubscriptions", expiredUsers.size());
            stats.put("totalMessages", botMessageService.getTotalMessages());
            stats.put("messagesToday", botMessageService.getMessagesToday());

            // Статистика по планам
            Map<String, Long> planStats = new HashMap<>();
            for (var user : allUsers) {
                String plan = user.getSubscriptionPlan().name();
                planStats.put(plan, planStats.getOrDefault(plan, 0L) + 1);
            }
            stats.put("planDistribution", planStats);

            // Статистика по ролям
            Map<String, Long> roleStats = new HashMap<>();
            for (var user : allUsers) {
                String role = user.getRole().name();
                roleStats.put(role, roleStats.getOrDefault(role, 0L) + 1);
            }
            stats.put("roleDistribution", roleStats);

            stats.put("lastUpdate", LocalDateTime.now());
            stats.put("botStatus", "online");

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting detailed stats", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to get detailed stats",
                    "message", e.getMessage()
            ));
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}