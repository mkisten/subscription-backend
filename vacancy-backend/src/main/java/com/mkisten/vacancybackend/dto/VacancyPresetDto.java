package com.mkisten.vacancybackend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VacancyPresetDto {

    private String id;
    private String name;
    private VacancyPresetSettingsDto settings;
    private String createdAt;
}
