package com.mkisten.vacancybackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class VacancyPresetSettingsDto {

    private String searchQuery = "";
    private Integer days = 1;
    private String excludeKeywords = "";
    private String excludeCompanies = "";
    private String cityId = "";
    private Set<String> workTypes = new LinkedHashSet<>();
    private Set<String> countries = new LinkedHashSet<>();
    private Boolean telegramNotify = false;
    private Boolean autoUpdateEnabled = false;
    private Integer autoUpdateInterval = 30;
    private String theme = "light";
}
