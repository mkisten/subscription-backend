package com.mkisten.hhparserbackend.repository;

import com.mkisten.hhparserbackend.entity.ScrapedVacancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScrapedVacancyRepository extends JpaRepository<ScrapedVacancy, Long> {

    List<ScrapedVacancy> findByExternalIdIn(Collection<String> externalIds);

    Page<ScrapedVacancy> findByTitleContainingIgnoreCaseOrEmployerNameContainingIgnoreCaseOrderByPublishedAtDesc(
            String title,
            String employer,
            Pageable pageable
    );

    Page<ScrapedVacancy> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
