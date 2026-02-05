package com.mkisten.subscriptionbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_services",
        uniqueConstraints = {
                @UniqueConstraint(name = "uniq_user_service", columnNames = {"user_id", "service_code"})
        },
        indexes = {
                @Index(name = "idx_user_services_user", columnList = "user_id"),
                @Index(name = "idx_user_services_service", columnList = "service_code"),
                @Index(name = "idx_user_services_end_date", columnList = "subscription_end_date")
        }
)
@Data
public class UserServiceSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false, length = 32)
    private ServiceCode serviceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.TRIAL;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Column(name = "trial_used", nullable = false)
    private Boolean trialUsed = false;

    @Column(name = "subscription_active", nullable = false)
    private Boolean subscriptionActive = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(subscriptionActive);
    }

    public void setActive(boolean active) {
        this.subscriptionActive = active;
    }
}
