package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findByUserIdOrderByPaymentDateDesc(Long userId);
    List<PaymentHistory> findByStatus(PaymentHistory.PaymentStatus status);
}