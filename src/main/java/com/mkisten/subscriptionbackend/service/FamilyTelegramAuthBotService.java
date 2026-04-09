package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.AuthSession;
import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyTelegramAuthBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token.family}")
    private String familyBotToken;

    @Value("${telegram.bot.username.family}")
    private String familyBotUsername;

    private final AuthSessionService authSessionService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Override
    public String getBotUsername() {
        return familyBotUsername;
    }

    @Override
    public String getBotToken() {
        return familyBotToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message msg = update.getMessage();
        String text = msg.getText();
        Long chatId = msg.getChatId();

        if (!text.startsWith("/start")) {
            sendText(chatId, "Используйте /start из ссылки авторизации.");
            return;
        }

        String[] parts = text.split(" ", 2);
        if (parts.length < 2 || !parts[1].startsWith("auth_")) {
            sendText(chatId, "Откройте ссылку авторизации из приложения Family.");
            return;
        }

        handleAuthPayload(msg, parts[1]);
    }

    private void handleAuthPayload(Message msg, String payload) {
        Long chatId = msg.getChatId();
        try {
            String params = payload.substring("auth_".length());
            String sessionId;
            String deviceId = "telegram_bot";

            int lastUnderscore = params.lastIndexOf('_');
            if (lastUnderscore > 0) {
                sessionId = params.substring(0, lastUnderscore);
                deviceId = params.substring(lastUnderscore + 1);
            } else {
                sessionId = params;
            }

            Optional<AuthSession> sessionOpt = authSessionService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                sendText(chatId, "Сессия не найдена или истекла. Запросите вход в приложении заново.");
                return;
            }
            if (sessionOpt.get().getStatus() != AuthSession.AuthStatus.PENDING) {
                sendText(chatId, "Сессия уже использована. Запросите новый вход в приложении.");
                return;
            }

            User user = userService.findByTelegramIdOptional(chatId).orElseGet(() ->
                    userService.createUser(
                            chatId,
                            msg.getFrom() != null ? msg.getFrom().getFirstName() : null,
                            msg.getFrom() != null ? msg.getFrom().getLastName() : null,
                            msg.getFrom() != null ? msg.getFrom().getUserName() : null
                    )
            );
            userService.getOrCreateService(user, ServiceCode.FAMILY);

            String jwtToken = jwtUtil.generateToken(chatId);
            authSessionService.completeAuthSession(sessionId, deviceId, chatId, jwtToken);
            sendText(chatId, "Авторизация Family подтверждена. Возвращайтесь в приложение.");
        } catch (Exception ex) {
            log.error("Family auth bot failed for chat {}", chatId, ex);
            sendText(chatId, "Ошибка авторизации. Попробуйте снова из приложения.");
        }
    }

    private void sendText(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send family auth bot message to {}", chatId, e);
        }
    }
}
