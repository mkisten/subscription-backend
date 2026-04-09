package com.mkisten.subscriptionbackend.config;

import com.mkisten.subscriptionbackend.service.FamilyTelegramAuthBotService;
import com.mkisten.subscriptionbackend.service.TelegramBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

    @Value("${telegram.bot.token.family:}")
    private String familyBotToken;

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService telegramBotService,
                                           FamilyTelegramAuthBotService familyTelegramAuthBotService) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBotService);
            if (familyBotToken != null
                    && !familyBotToken.isBlank()
                    && !familyBotToken.equals(telegramBotService.getBotToken())) {
                botsApi.registerBot(familyTelegramAuthBotService);
            }
            return botsApi;
        } catch (TelegramApiException e) {
            throw new RuntimeException("Failed to register Telegram bot", e);
        }
    }
}
