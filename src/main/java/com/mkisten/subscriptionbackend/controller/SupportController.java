package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.SupportMessageDto;
import com.mkisten.subscriptionbackend.dto.SupportMessageRequest;
import com.mkisten.subscriptionbackend.entity.SupportMessage;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.SupportMessageService;
import com.mkisten.subscriptionbackend.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Обратная связь пользователей")
public class SupportController {

    private final SupportMessageService supportMessageService;
    private final TelegramBotService telegramBotService;

    @Operation(summary = "Отправить сообщение в поддержку")
    @PostMapping("/messages")
    public ResponseEntity<?> createSupportMessage(
            @AuthenticationPrincipal User currentUser,
            @RequestBody SupportMessageRequest request
    ) {
        String message = request.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Message is required"
            ));
        }

        SupportMessage supportMessage = supportMessageService.createMessage(
                currentUser.getTelegramId(),
                message.trim(),
                "WEB"
        );
        telegramBotService.notifyAdminAboutSupport(supportMessage, null);

        return ResponseEntity.ok(Map.of(
                "message", "Support message sent",
                "id", supportMessage.getId()
        ));
    }

    @Operation(summary = "Получить свои сообщения поддержки")
    @GetMapping("/messages")
    public ResponseEntity<List<SupportMessageDto>> getMyMessages(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(supportMessageService.getUserMessages(currentUser.getTelegramId()));
    }
}
