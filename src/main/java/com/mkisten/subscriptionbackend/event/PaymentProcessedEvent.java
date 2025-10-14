package com.mkisten.subscriptionbackend.event;

import com.mkisten.subscriptionbackend.entity.Payment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentProcessedEvent extends ApplicationEvent {
    private final Payment payment;
    private final boolean approved;
    private final String notes;

    public PaymentProcessedEvent(Object source, Payment payment, boolean approved, String notes) {
        super(source);
        this.payment = payment;
        this.approved = approved;
        this.notes = notes;
    }
}