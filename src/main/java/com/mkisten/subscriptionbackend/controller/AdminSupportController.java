package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.SupportMessageDto;
import com.mkisten.subscriptionbackend.dto.SupportReplyRequest;
import com.mkisten.subscriptionbackend.entity.SupportMessage;
import com.mkisten.subscriptionbackend.service.SupportMessageService;
import com.mkisten.subscriptionbackend.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/support")
@RequiredArgsConstructor
@Tag(name = "Admin Support", description = "Администрирование обращений поддержки")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportController {

    private final SupportMessageService supportMessageService;
    private final TelegramBotService telegramBotService;

    @Operation(summary = "Список обращений поддержки")
    @GetMapping("/messages")
    public ResponseEntity<List<SupportMessageDto>> getAllMessages() {
        return ResponseEntity.ok(supportMessageService.getAllMessages());
    }

    @Operation(summary = "Ответить пользователю в поддержку")
    @PostMapping("/messages/{id}/reply")
    public ResponseEntity<?> replyToMessage(
            @PathVariable Long id,
            @RequestBody SupportReplyRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.mkisten.subscriptionbackend.entity.User currentUser
    ) {
        String reply = request.getReply();
        if (reply == null || reply.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Reply is required"
            ));
        }

        SupportMessage message = supportMessageService.replyMessage(
                id,
                reply.trim(),
                currentUser != null ? currentUser.getTelegramId() : null
        );
        telegramBotService.sendTextMessageToUser(message.getTelegramId(),
                "💬 **Ответ поддержки:**\n\n" + reply.trim());

        return ResponseEntity.ok(Map.of(
                "message", "Reply sent",
                "id", message.getId()
        ));
    }
}
