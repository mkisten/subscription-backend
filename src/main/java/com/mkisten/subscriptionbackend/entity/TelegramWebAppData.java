package com.mkisten.subscriptionbackend.entity;

import lombok.Builder;
import lombok.Data;

// Данные Web App
@Data
@Builder
public class TelegramWebAppData {
    private String initData;
    private String deviceId;
    private Platform platform;
}
