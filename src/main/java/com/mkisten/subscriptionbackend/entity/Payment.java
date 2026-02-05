// entity/Payment.java
package com.mkisten.subscriptionbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_code", nullable = false)
    private ServiceCode serviceCode = ServiceCode.VACANCY;

    @Column(name = "months", nullable = false)
    private Integer months;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "admin_notes")
    private String adminNotes;

    public enum PaymentStatus {
        PENDING,    // Ожидает оплаты
        VERIFIED,   // Оплата проверена
        REJECTED,   // Оплата отклонена
        EXPIRED     // Время на оплату истекло
    }

    public Payment(Long telegramId, Double amount, SubscriptionPlan plan, Integer months, ServiceCode serviceCode) {
        this.telegramId = telegramId;
        this.amount = amount;
        this.plan = plan;
        this.serviceCode = serviceCode;
        this.months = months;
        this.status = PaymentStatus.PENDING;
        this.phoneNumber = "+79779104605"; // Ваш номер Т-Банк
        this.createdAt = LocalDateTime.now();
    }
}
