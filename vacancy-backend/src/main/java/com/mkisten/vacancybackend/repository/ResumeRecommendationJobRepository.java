package com.mkisten.vacancybackend.repository;

import com.mkisten.vacancybackend.entity.ResumeRecommendationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRecommendationJobRepository extends JpaRepository<ResumeRecommendationJob, Long> {

    List<ResumeRecommendationJob> findByTelegramIdOrderByCreatedAtDesc(Long telegramId);

    Optional<ResumeRecommendationJob> findByIdAndTelegramId(Long id, Long telegramId);
}
