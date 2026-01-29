package com.mkisten.subscription.contract.dto.telegram;

import lombok.Data;

/**
 * /api/telegram-auth/create-session
 */
@Data
public class CreateSessionRequestDto {
    private String deviceId;
}
