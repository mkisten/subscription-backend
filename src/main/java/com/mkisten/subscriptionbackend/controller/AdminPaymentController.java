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

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

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

    @GetMapping("/all")
    public ResponseEntity<Page<Payment>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            Page<Payment> payments = paymentService.getAllPayments(pageable);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

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