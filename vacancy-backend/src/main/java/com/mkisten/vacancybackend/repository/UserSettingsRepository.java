package com.mkisten.vacancybackend.repository;

import com.mkisten.vacancybackend.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByTelegramId(Long telegramId);

    @Query("SELECT us FROM UserSettings us WHERE us.autoUpdateEnabled = true")
    List<UserSettings> findByAutoUpdateEnabledTrue();

    @Query("""
            SELECT us FROM UserSettings us
            WHERE us.autoUpdateEnabled = true
              AND (us.nextRunAt IS NULL OR us.nextRunAt <= :now)
            """)
    List<UserSettings> findDueUsers(@Param("now") LocalDateTime now, Pageable pageable);

    boolean existsByTelegramId(Long telegramId);
}
 
