package com.mkisten.hhparserbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "scraped_vacancies")
@Getter
@Setter
@NoArgsConstructor
public class ScrapedVacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "alternate_url", nullable = false, length = 1024)
    private String alternateUrl;

    @Column(name = "employer_name", length = 255)
    private String employerName;

    @Column(name = "area_name", length = 255)
    private String areaName;

    @Column(name = "salary_text", length = 512)
    private String salaryText;

    @Column(name = "salary_from")
    private Integer salaryFrom;

    @Column(name = "salary_to")
    private Integer salaryTo;

    @Column(name = "salary_currency", length = 16)
    private String salaryCurrency;

    @Column(name = "schedule_name", length = 255)
    private String scheduleName;

    @Column(name = "work_format_id", length = 64)
    private String workFormatId;

    @Column(name = "work_format_name", length = 255)
    private String workFormatName;

    @Column(name = "snippet_requirement", columnDefinition = "TEXT")
    private String snippetRequirement;

    @Column(name = "snippet_responsibility", columnDefinition = "TEXT")
    private String snippetResponsibility;

    @Column(name = "raw_published_text", length = 255)
    private String rawPublishedText;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
