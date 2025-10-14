package com.mkisten.subscriptionbackend.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bot_messages")
public class BotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "message_type", nullable = false)
    private String messageType; // TEXT, COMMAND, CALLBACK, etc.

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String direction; // IN или OUT

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}