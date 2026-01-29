package com.mkisten.vacancybackend.repository;

import com.mkisten.vacancybackend.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByTelegramId(Long telegramId);

    @Query("SELECT us FROM UserSettings us WHERE us.autoUpdateEnabled = true")
    List<UserSettings> findByAutoUpdateEnabledTrue();

    boolean existsByTelegramId(Long telegramId);
}
 