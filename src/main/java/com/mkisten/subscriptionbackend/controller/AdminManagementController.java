//package com.mkisten.subscriptionbackend.controller;
//
//import com.mkisten.subscriptionbackend.entity.User;
//import com.mkisten.subscriptionbackend.entity.UserRole;
//import com.mkisten.subscriptionbackend.service.UserService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.security.SecurityRequirement;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Map;
//
//@RestController
////@RequestMapping("/api/admin")
//@RequiredArgsConstructor
//@Tag(name = "Admin Management", description = "API для управления пользователями и правами")
//@SecurityRequirement(name = "JWT")
//@PreAuthorize("hasRole('ADMIN')") // Глобальная проверка для всего контроллера
//public class AdminManagementController {
//
//    private final UserService userService;
//
//    @Operation(summary = "Назначить роль пользователю")
//    @PostMapping("/users/{telegramId}/role")
//    public ResponseEntity<?> setUserRole(
//            @PathVariable Long telegramId,
//            @RequestParam UserRole role) {
//        try {
//            User user = userService.setUserRole(telegramId, role);
//            return ResponseEntity.ok(Map.of(
//                    "message", "Role updated successfully",
//                    "telegramId", user.getTelegramId(),
//                    "role", user.getRole()
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(Map.of(
//                    "error", "ROLE_UPDATE_FAILED",
//                    "message", e.getMessage()
//            ));
//        }
//    }
//
//    @Operation(summary = "Получить всех администраторов")
//    @GetMapping("/admins")
//    public ResponseEntity<?> getAllAdmins() {
//        try {
//            var admins = userService.findByRole(UserRole.ADMIN);
//            return ResponseEntity.ok(Map.of(
//                    "admins", admins,
//                    "count", admins.size()
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(Map.of(
//                    "error", "FETCH_FAILED",
//                    "message", e.getMessage()
//            ));
//        }
//    }
//
//    @Operation(summary = "Проверить права администратора")
//    @GetMapping("/check-access")
//    public ResponseEntity<?> checkAdminAccess() {
//        return ResponseEntity.ok(Map.of(
//                "message", "You have admin access",
//                "status", "OK"
//        ));
//    }
//}