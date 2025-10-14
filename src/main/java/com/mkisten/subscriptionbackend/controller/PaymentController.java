package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.dto.CreatePaymentRequest;
import com.mkisten.subscriptionbackend.entity.Payment;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.PaymentService;
import com.mkisten.subscriptionbackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    @PostMapping("/create")
    public ResponseEntity<Payment> createPayment(
            @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);

            Payment payment = paymentService.createPayment(
                    user.getTelegramId(),
                    request.getPlan(),
                    request.getMonths()
            );
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/my-payments")
    public ResponseEntity<List<Payment>> getUserPayments(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);

            List<Payment> payments = paymentService.getUserPayments(user.getTelegramId());
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<Payment> checkPaymentStatus(@PathVariable Long paymentId) {
        try {
            return paymentService.getPaymentById(paymentId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Void> cancelPayment(@PathVariable Long paymentId) {
        try {
            paymentService.rejectPayment(paymentId, "Отменено пользователем");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // Уведомление администратора о платеже (когда пользователь нажал "Подтвердить оплату")
    @PostMapping("/{paymentId}/notify-admin")
    public ResponseEntity<Void> notifyAdminAboutPayment(@PathVariable Long paymentId) {
        try {
            paymentService.notifyAdminAboutPayment(paymentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}