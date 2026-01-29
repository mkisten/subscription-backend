package com.mkisten.vacancybackend.api.error;

import lombok.Getter;

@Getter
public class SubscriptionRequiredException extends RuntimeException {

    private final String path;

    public SubscriptionRequiredException(String message, String path) {
        super(message);
        this.path = path;
    }
}
