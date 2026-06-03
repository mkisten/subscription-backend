package com.mkisten.vacancybackend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveVacancyPresetRequest {

    private String name;
    private VacancyPresetSettingsDto settings;
}
