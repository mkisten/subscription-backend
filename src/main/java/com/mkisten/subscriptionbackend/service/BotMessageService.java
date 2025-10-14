package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.BotMessage;
import com.mkisten.subscriptionbackend.repository.BotMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotMessageService {

    private final BotMessageRepository botMessageRepository;

    public void logMessage(Long telegramId, String messageType, String content, String direction) {
        try {
            BotMessage message = new BotMessage();
            message.setTelegramId(telegramId);
            message.setMessageType(messageType);
            message.setContent(content);
            message.setDirection(direction);
            message.setCreatedAt(LocalDateTime.now());

            botMessageRepository.save(message);
        } catch (Exception e) {
            log.error("Error logging bot message", e);
        }
    }

    public long getTotalMessages() {
        return botMessageRepository.count();
    }

    public long getMessagesToday() {
        try {
            // Используем альтернативный подход без сложных JPQL запросов
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime startOfNextDay = today.plusDays(1).atStartOfDay();

            return botMessageRepository.countByCreatedAtBetween(startOfDay, startOfNextDay);
        } catch (Exception e) {
            log.error("Error counting today's messages", e);
            return 0;
        }
    }
}