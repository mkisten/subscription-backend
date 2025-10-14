package com.mkisten.subscriptionbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// Данные пользователя Telegram
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUser {
    private Long id;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String username;

    @JsonProperty("photo_url")
    private String photoUrl;
}
