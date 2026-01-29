package com.mkisten.vacancybackend.api.error;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ApiErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;   // текстовое имя статуса: UNAUTHORIZED, FORBIDDEN и т.д.
    private String code;    // компактный машинный код: AUTH_TOKEN_INVALID
    private String message; // человекочитаемое сообщение
    private String path;    // URL, на который пришёл запрос
}