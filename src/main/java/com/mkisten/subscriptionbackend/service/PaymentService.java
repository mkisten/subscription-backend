package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.controller.AdminPaymentController;
import com.mkisten.subscriptionbackend.entity.Payment;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.event.PaymentNotificationEvent;
import com.mkisten.subscriptionbackend.event.PaymentProcessedEvent;
import com.mkisten.subscriptionbackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;


    // Цены для тарифов (в рублях)
    private static final double MONTHLY_PRICE = 299.0;
    private static final double YEARLY_PRICE = 2990.0;
    private static final double LIFETIME_PRICE = 9990.0;

    public Payment createPayment(Long telegramId, SubscriptionPlan plan, Integer months) {
        double amount = calculateAmount(plan, months);

        Payment payment = new Payment(telegramId, amount, plan, months);
        return paymentRepository.save(payment);
    }

    // Получение всех платежей с фильтрацией по статусу
    public Page<Payment> getAllPaymentsWithFilter(Payment.PaymentStatus status, Pageable pageable) {
        if (status != null) {
            return paymentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            return paymentRepository.findAll(pageable);
        }
    }

    // Получение всех статусов платежей
    public List<Payment.PaymentStatus> getAllPaymentStatuses() {
        return List.of(Payment.PaymentStatus.values());
    }

    private double calculateAmount(SubscriptionPlan plan, Integer months) {
        switch (plan) {
            case MONTHLY:
                return MONTHLY_PRICE * months;
            case YEARLY:
                return YEARLY_PRICE * (months / 12.0);
            case LIFETIME:
                return LIFETIME_PRICE;
            default:
                throw new IllegalArgumentException("Unknown plan: " + plan);
        }
    }

    public List<Payment> getUserPayments(Long telegramId) {
        return paymentRepository.findByTelegramIdOrderByCreatedAtDesc(telegramId);
    }

    public Optional<Payment> getPendingPayment(Long telegramId) {
        return paymentRepository.findFirstByTelegramIdAndStatusOrderByCreatedAtDesc(
                telegramId, Payment.PaymentStatus.PENDING);
    }

    public Optional<Payment> getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    @Transactional
    public Payment verifyPayment(Long paymentId, String adminNotes) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }

        Payment payment = paymentOpt.get();

        // Продлеваем подписку пользователя
        User user = userService.findByTelegramId(payment.getTelegramId());
        userService.extendSubscription(user.getTelegramId(), payment.getMonths() * 30, payment.getPlan());

        // Обновляем статус платежа
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setAdminNotes(adminNotes);

        Payment savedPayment = paymentRepository.save(payment);

        // Публикуем событие вместо прямого вызова
        eventPublisher.publishEvent(new PaymentProcessedEvent(this, payment, true, adminNotes));

        log.info("Payment verified: {} for user {}", paymentId, payment.getTelegramId());
        return savedPayment;
    }

    @Transactional
    public Payment rejectPayment(Long paymentId, String reason) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }

        Payment payment = paymentOpt.get();
        payment.setStatus(Payment.PaymentStatus.REJECTED);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setAdminNotes("Отклонено: " + reason);

        Payment savedPayment = paymentRepository.save(payment);

        // Публикуем событие вместо прямого вызова
        eventPublisher.publishEvent(new PaymentProcessedEvent(this, payment, false, reason));

        return savedPayment;
    }

    // Автоматическая отмена просроченных платежей (каждый час)
    @Scheduled(fixedRate = 3600000) // 1 час
    @Transactional
    public void cancelExpiredPayments() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(24); // 24 часа на оплату
        List<Payment> expiredPayments = paymentRepository.findExpiredPayments(expiryTime);

        for (Payment payment : expiredPayments) {
            payment.setStatus(Payment.PaymentStatus.EXPIRED);
            paymentRepository.save(payment);

            // Публикуем событие для уведомления об истечении
            eventPublisher.publishEvent(new PaymentProcessedEvent(this, payment, false, "Время на оплату истекло"));

            log.info("Payment expired: {}", payment.getId());
        }
    }

    public List<Payment> getPendingPayments() {
        return paymentRepository.findByStatusOrderByCreatedAtDesc(Payment.PaymentStatus.PENDING);
    }

    // Уведомление администратора о платеже
    @Transactional
    public void notifyAdminAboutPayment(Long paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }

        Payment payment = paymentOpt.get();

        // Публикуем событие вместо прямого вызова
        eventPublisher.publishEvent(new PaymentNotificationEvent(this, paymentId, payment.getTelegramId()));

        log.info("Admin notification event published for payment: {} from user {}", paymentId, payment.getTelegramId());
    }

    // Получение всех платежей с пагинацией
    public Page<Payment> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    // Статистика платежей
    public AdminPaymentController.PaymentStats getPaymentStats() {
        AdminPaymentController.PaymentStats stats = new AdminPaymentController.PaymentStats();

        stats.totalPayments = paymentRepository.count();
        stats.pendingPayments = paymentRepository.countByStatus(Payment.PaymentStatus.PENDING);
        stats.verifiedPayments = paymentRepository.countByStatus(Payment.PaymentStatus.VERIFIED);
        stats.rejectedPayments = paymentRepository.countByStatus(Payment.PaymentStatus.REJECTED);

        // Используем исправленный метод
        Double totalRevenue = paymentRepository.sumAmountByStatus(Payment.PaymentStatus.VERIFIED);
        stats.totalRevenue = totalRevenue != null ? totalRevenue : 0.0;

        return stats;
    }
}