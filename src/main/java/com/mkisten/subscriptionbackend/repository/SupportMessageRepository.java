package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    List<SupportMessage> findByTelegramIdOrderByCreatedAtDesc(Long telegramId);
}
