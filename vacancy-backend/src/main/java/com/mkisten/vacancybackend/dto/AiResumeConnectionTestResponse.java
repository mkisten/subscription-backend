package com.mkisten.vacancybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiResumeConnectionTestResponse {
    private boolean success;
    private String message;
    private String model;
}
