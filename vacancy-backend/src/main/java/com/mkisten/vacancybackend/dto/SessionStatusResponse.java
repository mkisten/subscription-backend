package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class SessionStatusResponse {

    private String sessionId;
    private String deviceId;
    private String status;
    private String message;
    private String token;
}
