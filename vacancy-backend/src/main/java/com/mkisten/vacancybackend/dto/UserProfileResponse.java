package com.mkisten.vacancybackend.dto;

import lombok.Data;

@Data
public class UserProfileResponse {

    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phone;
    private String role;
    private boolean active;
}
