package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    List<SupportMessage> findByTelegramIdOrderByCreatedAtDesc(Long telegramId);

    @Modifying
    @Query("update SupportMessage s set s.status = 'READ' where s.telegramId = :telegramId and s.status = 'NEW'")
    int markReadByTelegramId(@Param("telegramId") Long telegramId);
}
