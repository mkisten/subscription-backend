package com.mkisten.subscriptionbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_history")
@Data
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String paymentProvider;
    private String transactionId;

    private LocalDateTime paymentDate;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REFUNDED
    }
}