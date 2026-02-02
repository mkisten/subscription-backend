package com.mkisten.subscriptionbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupportMessageDto {
    private Long id;
    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
    private String message;
    private LocalDateTime createdAt;
    private String source;
    private String status;
    private String adminReply;
    private LocalDateTime repliedAt;
    private Long adminTelegramId;
}
