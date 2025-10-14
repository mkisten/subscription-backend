package com.mkisten.subscriptionbackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentNotificationEvent extends ApplicationEvent {
    private final Long paymentId;
    private final Long telegramId;

    public PaymentNotificationEvent(Object source, Long paymentId, Long telegramId) {
        super(source);
        this.paymentId = paymentId;
        this.telegramId = telegramId;
    }
}