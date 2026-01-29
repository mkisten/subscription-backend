package com.mkisten.subscription.contract.dto.telegram;

import lombok.Data;

/**
 * /api/telegram-auth/status/{sessionId}
 * Объединяет твой AuthStatusResponse и vacancy SessionStatusResponse.
 */
@Data
public class SessionStatusDto {

    private String sessionId;
    private String deviceId;

    /**
     * Строковое значение enum’а AuthStatus: PENDING / COMPLETED / FAILED / EXPIRED / NOT_FOUND.
     */
    private String status;

    private String message;

    /**
     * JWT токен, если авторизация завершилась успешно.
     */
    private String token;
}
