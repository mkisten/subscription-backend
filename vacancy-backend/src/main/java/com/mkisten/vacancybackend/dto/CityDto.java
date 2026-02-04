package com.mkisten.vacancybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CityDto {
    private String id;
    private String name;
    private String countryId;
}
