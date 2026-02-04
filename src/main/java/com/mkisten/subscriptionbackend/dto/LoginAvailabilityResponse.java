package com.mkisten.subscriptionbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginAvailabilityResponse {
    private boolean available;
    private String normalizedLogin;
}
