package com.mkisten.subscription.contract.dto.common;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiErrorDto {

    private LocalDateTime timestamp;
    private int status;
    private String error;

    /**
     * Машинный код ошибки, по нему фронт принимает решение:
     * UNAUTHORIZED, INVALID_TOKEN, SUBSCRIPTION_INACTIVE, SUBSCRIPTION_EXPIRED и т.п.
     */
    private String code;

    /**
     * Человекочитаемое сообщение. Может быть локализовано по code на фронте.
     */
    private String message;
}
