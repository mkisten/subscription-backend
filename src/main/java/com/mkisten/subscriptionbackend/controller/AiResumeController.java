package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.AiResumeAccessStatusResponse;
import com.mkisten.subscriptionbackend.dto.AiResumeConsumeResponse;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.AiResumeCreditService;
import com.mkisten.subscriptionbackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-resume")
@RequiredArgsConstructor
public class AiResumeController {

    private final AiResumeCreditService aiResumeCreditService;
    private final UserService userService;

    @GetMapping("/status")
    public ResponseEntity<AiResumeAccessStatusResponse> getStatus(Authentication authentication) {
        return ResponseEntity.ok(aiResumeCreditService.getStatus(resolveUser(authentication)));
    }

    @PostMapping("/consume")
    public ResponseEntity<AiResumeConsumeResponse> consume(Authentication authentication) {
        return ResponseEntity.ok(aiResumeCreditService.consume(resolveUser(authentication)));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        String name = authentication != null ? authentication.getName() : null;
        if (name != null) {
            try {
                Long telegramId = Long.parseLong(name);
                return userService.findByTelegramId(telegramId);
            } catch (NumberFormatException ignored) {
                // fall through
            }
            return userService.findByUsername(name);
        }
        throw new IllegalStateException("Authenticated user not found");
    }
}
