package com.mkisten.vacancybackend.entity;

import java.io.Serializable;
import java.util.Objects;

public class VacancyKey implements Serializable {
    private String id;
    private Long userTelegramId;

    public VacancyKey() {}

    public VacancyKey(String id, Long userTelegramId) {
        this.id = id;
        this.userTelegramId = userTelegramId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VacancyKey that = (VacancyKey) o;
        return Objects.equals(id, that.id) && Objects.equals(userTelegramId, that.userTelegramId);
    }


    @Override
    public int hashCode() {
        return Objects.hash(id, userTelegramId);
    }

    // Геттеры и сеттеры
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserTelegramId() {
        return userTelegramId;
    }

    public void setUserTelegramId(Long userTelegramId) {
        this.userTelegramId = userTelegramId;
    }
}
