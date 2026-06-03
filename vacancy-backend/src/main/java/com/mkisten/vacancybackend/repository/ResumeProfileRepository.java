package com.mkisten.vacancybackend.repository;

import com.mkisten.vacancybackend.entity.ResumeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeProfileRepository extends JpaRepository<ResumeProfile, Long> {

    List<ResumeProfile> findByTelegramIdOrderByUpdatedAtDesc(Long telegramId);

    Optional<ResumeProfile> findFirstByTelegramIdAndActiveTrueOrderByUpdatedAtDesc(Long telegramId);

    Optional<ResumeProfile> findByIdAndTelegramId(Long id, Long telegramId);

    @Modifying
    @Query("update ResumeProfile r set r.active = false where r.telegramId = :telegramId")
    void deactivateAllByTelegramId(@Param("telegramId") Long telegramId);
}
