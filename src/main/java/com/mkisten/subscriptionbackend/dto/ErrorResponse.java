package com.mkisten.subscriptionbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;

    // Конструктор только с error для обратной совместимости
    public ErrorResponse(String error) {
        this.error = error;
        this.message = error;
    }
}