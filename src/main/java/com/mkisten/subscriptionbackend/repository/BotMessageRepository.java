package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.BotMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
public interface BotMessageRepository extends JpaRepository<BotMessage, Long> {

//    // Исправленный запрос - используем LocalDate для сравнения
//    @Query("SELECT COUNT(m) FROM BotMessage m WHERE DATE(m.createdAt) = CURRENT_DATE")
//    long countMessagesToday();
//
//    // Альтернативный вариант с явным указанием даты
//    @Query("SELECT COUNT(m) FROM BotMessage m WHERE m.createdAt >= :startOfDay AND m.createdAt < :startOfNextDay")
//    long countMessagesTodayAlternative(
//            @Param("startOfDay") LocalDateTime startOfDay,
//            @Param("startOfNextDay") LocalDateTime startOfNextDay
//    );
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}