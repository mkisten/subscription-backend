package com.mkisten.subscriptionbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
public class AuthSession {

    @Id
    private String sessionId;

    @Column(nullable = false)
    private String deviceId;

    private Long telegramId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthStatus status;

    private String jwtToken;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;

    // Конструкторы
    public AuthSession() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public AuthSession(String sessionId, String deviceId, AuthStatus status) {
        this();
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.status = status;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum AuthStatus {
        PENDING,
        COMPLETED,
        EXPIRED
    }
}