package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class AuthResponse {

    private boolean valid;
    public AuthResponse(boolean valid) { this.valid = valid; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
}