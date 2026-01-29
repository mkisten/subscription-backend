package com.mkisten.vacancybackend.dto;

import com.mkisten.vacancybackend.entity.Vacancy;

import com.mkisten.vacancybackend.entity.VacancyStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VacancyResponse {
    private String id;
    private String title;
    private String employer;
    private String city;
    private String schedule;
    private String salary;
    private String url;
    private VacancyStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime loadedAt;

    // Конструктор из entity
    public VacancyResponse(Vacancy vacancy) {

        this.id = vacancy.getId();
        this.title = vacancy.getTitle();
        this.employer = vacancy.getEmployer();
        this.city = vacancy.getCity();
        this.schedule = vacancy.getSchedule();
        this.salary = vacancy.getSalary();
        this.url = vacancy.getUrl();
        this.status = vacancy.getStatus();
        this.publishedAt = vacancy.getPublishedAt();
        this.loadedAt = vacancy.getLoadedAt();
    }
}