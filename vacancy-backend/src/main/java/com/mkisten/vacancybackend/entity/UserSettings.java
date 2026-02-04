package com.mkisten.vacancybackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {

    @Id
    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "search_query", nullable = false)
    private String searchQuery = "Python разработчик";

    @Column(name = "days")
    private Integer days = 1;

    @Column(name = "exclude_keywords", length = 1000)
    private String excludeKeywords = "";

    @Column(name = "city_id")
    private String cityId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_work_types",
            joinColumns = @JoinColumn(name = "telegram_id")
    )
    @Column(name = "work_type")
    private Set<String> workTypes = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_countries",
            joinColumns = @JoinColumn(name = "telegram_id")
    )
    @Column(name = "country")
    private Set<String> countries = new HashSet<>();

    @Column(name = "telegram_notify")
    private Boolean telegramNotify = false;

    @Column(name = "auto_update_enabled")
    private Boolean autoUpdateEnabled = false;

    @Column(name = "auto_update_interval")
    private Integer autoUpdateInterval = 30;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "theme")
    private String theme = "light";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // Конструкторы
    public UserSettings() {}

    public UserSettings(Long telegramId) {
        this.telegramId = telegramId;
        // Значения по умолчанию
        this.workTypes.add("remote");
        this.countries.add("russia");
    }
}
