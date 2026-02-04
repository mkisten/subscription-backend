package com.mkisten.subscriptionbackend.dto;

import lombok.Data;

@Data
public class CredentialsRequest {
    private String login;
    private String password;
}
