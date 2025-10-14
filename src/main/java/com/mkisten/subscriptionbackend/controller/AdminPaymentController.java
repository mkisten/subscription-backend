package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.entity.Payment;
import com.mkisten.subscriptionbackend.service.PaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    // Существующий endpoint для ожидающих платежей
    @GetMapping("/pending")
    public ResponseEntity<List<Payment>> getPendingPayments() {
        try {
            List<Payment> pendingPayments = paymentService.getPendingPayments();
            return ResponseEntity.ok(pendingPayments);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // Обновленный endpoint для всех платежей с фильтрацией
    @GetMapping("/all")
    public ResponseEntity<?> getAllPayments(
            @RequestParam(required = false) Payment.PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            Page<Payment> payments = paymentService.getAllPaymentsWithFilter(status, pageable);

            // Возвращаем структурированный ответ с пагинацией
            Map<String, Object> response = Map.of(
                    "payments", payments.getContent(),
                    "currentPage", payments.getNumber(),
                    "totalItems", payments.getTotalElements(),
                    "totalPages", payments.getTotalPages(),
                    "hasNext", payments.hasNext(),
                    "hasPrevious", payments.hasPrevious()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // Новый endpoint для получения всех статусов
    @GetMapping("/statuses")
    public ResponseEntity<List<Payment.PaymentStatus>> getPaymentStatuses() {
        try {
            List<Payment.PaymentStatus> statuses = paymentService.getAllPaymentStatuses();
            return ResponseEntity.ok(statuses);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // Существующие методы остаются без изменений
    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable Long paymentId) {
        try {
            return paymentService.getPaymentById(paymentId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{paymentId}/verify")
    public ResponseEntity<Payment> verifyPayment(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String notes) {
        try {
            Payment verifiedPayment = paymentService.verifyPayment(paymentId, notes);
            return ResponseEntity.ok(verifiedPayment);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{paymentId}/reject")
    public ResponseEntity<Payment> rejectPayment(
            @PathVariable Long paymentId,
            @RequestParam String reason) {
        try {
            Payment rejectedPayment = paymentService.rejectPayment(paymentId, reason);
            return ResponseEntity.ok(rejectedPayment);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<PaymentStats> getPaymentStats() {
        try {
            PaymentStats stats = paymentService.getPaymentStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // DTO для статистики
    @Data
    public static class PaymentStats {
        public long totalPayments;
        public long pendingPayments;
        public long verifiedPayments;
        public long rejectedPayments;
        public double totalRevenue;

        public PaymentStats() {}

        public PaymentStats(long totalPayments, long pendingPayments, long verifiedPayments,
                            long rejectedPayments, double totalRevenue) {
            this.totalPayments = totalPayments;
            this.pendingPayments = pendingPayments;
            this.verifiedPayments = verifiedPayments;
            this.rejectedPayments = rejectedPayments;
            this.totalRevenue = totalRevenue;
        }
    }
}