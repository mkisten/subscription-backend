package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class AuthRequest {

    private Long telegramId;
    private String deviceId; // опционально для Telegram-авторизации

}
