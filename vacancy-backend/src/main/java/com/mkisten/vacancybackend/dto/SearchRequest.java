package com.mkisten.vacancybackend.dto;

import lombok.Data;
import java.util.Set;

@Data
public class SearchRequest {

    private String query;
    private Integer days = 1;
    private Set<String> workTypes;
    private Set<String> countries;
    private String excludeKeywords;
    private Boolean telegramNotify = false;
    private Long telegramId;
}