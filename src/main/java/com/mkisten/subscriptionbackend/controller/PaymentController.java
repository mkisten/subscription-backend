package com.mkisten.subscriptionbackend.controller;

import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscription.contract.dto.payment.CreatePaymentRequestDto;
import com.mkisten.subscription.contract.dto.payment.PaymentResponseDto;
import com.mkisten.subscriptionbackend.entity.Payment;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.PaymentService;
import com.mkisten.subscriptionbackend.service.UserService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<PaymentResponseDto> createPayment(
            @RequestBody CreatePaymentRequestDto request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);

        SubscriptionPlan plan = request.getPlan() != null
                ? SubscriptionPlan.valueOf(request.getPlan().name())
                : SubscriptionPlan.TRIAL;

        ServiceCode serviceCode = request.getService() != null
                ? ServiceCode.valueOf(request.getService().name())
                : ServiceCode.VACANCY;

        Payment payment = paymentService.createPayment(
                user.getTelegramId(),
                plan,
                request.getMonths(),
                serviceCode
        );

        PaymentResponseDto dto = mapPayment(payment);
        return ResponseEntity.ok(dto);
    }

    private PaymentResponseDto mapPayment(Payment payment) {
        PaymentResponseDto dto = new PaymentResponseDto();
        dto.setId(payment.getId());
        dto.setTelegramId(payment.getTelegramId());
        dto.setAmount(payment.getAmount());
        dto.setPlan(com.mkisten.subscription.contract.enums.SubscriptionPlanDto.valueOf(payment.getPlan().name()));
        dto.setService(com.mkisten.subscription.contract.enums.ServiceCodeDto.valueOf(payment.getServiceCode().name()));
        dto.setMonths(payment.getMonths());
        dto.setStatus(payment.getStatus().name());
        dto.setPhoneNumber(payment.getPhoneNumber());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setVerifiedAt(payment.getVerifiedAt());
        dto.setAdminNotes(payment.getAdminNotes());
        return dto;
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
