package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ✅ Правильно - Spring Data сам создаст запрос
    List<Payment> findByTelegramIdOrderByCreatedAtDesc(Long telegramId);

    // ✅ Правильно - Spring Data сам создаст запрос
    List<Payment> findByStatusOrderByCreatedAtDesc(Payment.PaymentStatus status);

    // ✅ Правильно - Spring Data сам создаст запрос
    Optional<Payment> findFirstByTelegramIdAndStatusOrderByCreatedAtDesc(
            Long telegramId, Payment.PaymentStatus status);

    // ⚠️ Исправить - добавить аннотацию @Param
    @Query("SELECT p FROM Payment p WHERE p.createdAt < :expiryTime AND p.status = 'PENDING'")
    List<Payment> findExpiredPayments(@Param("expiryTime") LocalDateTime expiryTime);

    // ✅ Правильно - Spring Data сам создаст запрос
    long countByStatus(Payment.PaymentStatus status);

    // ❌ ОШИБКА - нужно использовать @Query или правильное имя метода
    // Исправим на:
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    Double sumAmountByStatus(@Param("status") Payment.PaymentStatus status);

    // ✅ Дополнительные полезные методы:

    // Поиск платежей по Telegram ID и статусу
    List<Payment> findByTelegramIdAndStatusOrderByCreatedAtDesc(
            Long telegramId, Payment.PaymentStatus status);

    // Поиск платежей за период
    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    List<Payment> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Статистика по дням
    @Query("SELECT DATE(p.createdAt), COUNT(p), SUM(p.amount) FROM Payment p WHERE p.status = 'VERIFIED' AND p.createdAt BETWEEN :startDate AND :endDate GROUP BY DATE(p.createdAt) ORDER BY DATE(p.createdAt) DESC")
    List<Object[]> getDailyStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Получить последний платеж пользователя
    Optional<Payment> findTopByTelegramIdOrderByCreatedAtDesc(Long telegramId);
}