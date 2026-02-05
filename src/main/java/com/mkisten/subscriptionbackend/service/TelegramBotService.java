package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.entity.AuthSession;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.SubscriptionPlan;
import com.mkisten.subscriptionbackend.entity.Payment;
import com.mkisten.subscriptionbackend.entity.SupportMessage;
import com.mkisten.subscriptionbackend.event.PaymentNotificationEvent;
import com.mkisten.subscriptionbackend.event.PaymentProcessedEvent;
import com.mkisten.subscriptionbackend.repository.PaymentRepository;
import com.mkisten.subscriptionbackend.security.JwtUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username:hhsubscription_bot}")
    private String botUsername;

    @Value("${admin.chat.id:6927880904}")
    private String adminChatId;

    private final UserService userService;
    private final TelegramAuthService telegramAuthService;
    private final AuthSessionService authSessionService;
    private final JwtUtil jwtUtil;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final BotMessageService botMessageService;
    private final SupportMessageService supportMessageService;

    private final Set<Long> supportWaitingUsers = ConcurrentHashMap.newKeySet();
    private final Set<Long> loginWaitingUsers = ConcurrentHashMap.newKeySet();
    private final Set<Long> passwordWaitingUsers = ConcurrentHashMap.newKeySet();

    @Getter
    public enum SubscriptionPlanWithPrice {
        MONTHLY(299, "Месячная", 1),
        YEARLY(2990, "Годовая", 12),
        LIFETIME(9990, "Пожизненная", 999);

        private final double price;
        private final String description;
        private final int months;

        SubscriptionPlanWithPrice(double price, String description, int months) {
            this.price = price;
            this.description = description;
            this.months = months;
        }

        public SubscriptionPlan toSubscriptionPlan() {
            return SubscriptionPlan.valueOf(this.name());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    @EventListener
    public void handlePaymentNotificationEvent(PaymentNotificationEvent event) {
        try {
            notifyAdminAboutPaymentCheck(event.getPaymentId(), event.getTelegramId());
        } catch (Exception e) {
            log.error("Error handling payment notification event", e);
        }
    }

    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = message.getFrom();

        log.info("Received message from {}: {}", chatId, text);

        if (supportWaitingUsers.contains(chatId)) {
            if (text != null && text.startsWith("/")) {
                if ("/cancel".equals(text)) {
                    supportWaitingUsers.remove(chatId);
                    sendTextMessage(chatId, "❌ Отправка сообщения в поддержку отменена.");
                    return;
                }
            } else {
                handleSupportMessage(chatId, telegramUser, text);
                return;
            }
        }

        if (loginWaitingUsers.contains(chatId)) {
            if (text != null && text.startsWith("/")) {
                if ("/cancel".equals(text)) {
                    loginWaitingUsers.remove(chatId);
                    sendTextMessage(chatId, "❌ Установка логина отменена.");
                    return;
                }
            } else {
                handleLoginUpdate(chatId, text);
                return;
            }
        }

        if (passwordWaitingUsers.contains(chatId)) {
            if (text != null && text.startsWith("/")) {
                if ("/cancel".equals(text)) {
                    passwordWaitingUsers.remove(chatId);
                    sendTextMessage(chatId, "❌ Установка пароля отменена.");
                    return;
                }
            } else {
                handlePasswordUpdate(chatId, text);
                return;
            }
        }

        if (text.startsWith("/start")) {
            handleStartCommand(chatId, text, telegramUser);
        } else if (text.equals("/register")) {
            handleRegisterCommand(chatId, telegramUser);
        } else if (text.equals("/auth")) {
            handleAuthCommand(chatId, telegramUser);
        } else if (text.equals("/profile")) {
            handleProfileCommand(chatId);
        } else if (text.equals("/status")) {
            handleStatusCommand(chatId);
        } else if (text.equals("/help")) {
            handleHelpCommand(chatId);
        } else if (text.equals("/support")) {
            handleSupportCommand(chatId);
        } else if (text.equals("/admin")) {
            handleAdminCommand(chatId);
        } else if (text.equals("/pay") || text.equals("/payment")) {
            handlePayCommand(chatId);
        } else if (text.equals("/my_payments") || text.equals("/payments")) {
            handleMyPaymentsCommand(chatId);
        } else if (text.startsWith("/verify")) {
            handleAdminVerifyCommand(chatId, text);
        } else if (text.startsWith("/reject")) {
            handleAdminRejectCommand(chatId, text);
        } else {
            handleUnknownCommand(chatId);
        }
    }

    private void handleAdminVerifyCommand(Long chatId, String text) {
        if (!isAdmin(chatId)) {
            sendTextMessage(chatId, "❌ У вас нет прав администратора.");
            return;
        }

        try {
            // Формат: /verify 123 комментарий
            String[] parts = text.split(" ", 3);
            if (parts.length < 2) {
                sendTextMessage(chatId, "❌ Формат: /verify <ID_платежа> [комментарий]");
                return;
            }

            Long paymentId = Long.parseLong(parts[1]);
            String notes = parts.length > 2 ? parts[2] : null;

            // Здесь будет вызов API для подтверждения платежа
            // Пока просто отправляем сообщение
            sendTextMessage(chatId,
                    "✅ **Команда подтверждения платежа**\n\n" +
                            "🆔 ID: " + paymentId + "\n" +
                            "📝 Комментарий: " + (notes != null ? notes : "не указан") + "\n\n" +
                            "ℹ️ Используйте API для реального подтверждения: POST /api/admin/payments/" + paymentId + "/verify");

        } catch (Exception e) {
            log.error("Error verifying payment via bot", e);
            sendTextMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleAdminRejectCommand(Long chatId, String text) {
        if (!isAdmin(chatId)) {
            sendTextMessage(chatId, "❌ У вас нет прав администратора.");
            return;
        }

        try {
            // Формат: /reject 123 причина
            String[] parts = text.split(" ", 3);
            if (parts.length < 3) {
                sendTextMessage(chatId, "❌ Формат: /reject <ID_платежа> <причина>");
                return;
            }

            Long paymentId = Long.parseLong(parts[1]);
            String reason = parts[2];

            // Здесь будет вызов API для отклонения платежа
            // Пока просто отправляем сообщение
            sendTextMessage(chatId,
                    "❌ **Команда отклонения платежа**\n\n" +
                            "🆔 ID: " + paymentId + "\n" +
                            "📝 Причина: " + reason + "\n\n" +
                            "ℹ️ Используйте API для реального отклонения: POST /api/admin/payments/" + paymentId + "/reject");

        } catch (Exception e) {
            log.error("Error rejecting payment via bot", e);
            sendTextMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    // Обработчик событий платежей
    @EventListener
    public void handlePaymentProcessedEvent(PaymentProcessedEvent event) {
        try {
            Payment payment = event.getPayment();
            boolean approved = event.isApproved();
            String notes = event.getNotes();

            notifyUserAboutPaymentResult(payment, approved, notes);

        } catch (Exception e) {
            log.error("Error handling payment processed event", e);
        }
    }

    private void notifyUserAboutPaymentResult(Payment payment, boolean approved, String notes) {
        try {
            String message;
            if (approved) {
                message = "✅ **Платеж подтвержден!**\n\n" +
                        "💰 Сумма: " + payment.getAmount() + " ₽\n" +
                        "💎 Тариф: " + payment.getPlan().getDescription() + "\n" +
                        "📅 Срок: " + payment.getMonths() + " месяцев\n" +
                        "🆔 ID платежа: `" + payment.getId() + "`\n\n";

                if (notes != null && !notes.trim().isEmpty()) {
                    message += "📝 Комментарий администратора: " + notes + "\n\n";
                }

                message += "🎉 Ваша подписка успешно продлена!\n\n" +
                        "🔐 Вы можете авторизоваться в приложении командой `/auth`";

            } else {
                message = "❌ **Платеж отклонен**\n\n" +
                        "💰 Сумма: " + payment.getAmount() + " ₽\n" +
                        "💎 Тариф: " + payment.getPlan().getDescription() + "\n" +
                        "🆔 ID платежа: `" + payment.getId() + "`\n\n" +
                        "📝 Причина: " + notes + "\n\n" +
                        "ℹ️ Если вы уверены, что оплатили, свяжитесь с поддержкой.\n" +
                        "💳 Для создания нового платежа отправьте `/pay`";
            }

            sendTextMessageToUser(payment.getTelegramId(), message);
            log.info("Payment result notification sent to user: {}", payment.getTelegramId());

        } catch (Exception e) {
            log.error("Error notifying user about payment result: {}", payment.getTelegramId(), e);
        }
    }

    private void handlePayCommand(Long chatId) {
        try {
            User user = userService.findByTelegramId(chatId);
            sendPlanSelection(chatId);
        } catch (Exception e) {
            sendTextMessage(chatId,
                    "❌ **Сначала необходимо зарегистрироваться**\n\n" +
                            "Отправьте `/register` для регистрации.");
        }
    }

    private void sendPlanSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💎 **Выберите тариф для оплаты:**\n\n" +
                "Платеж осуществляется через Т-Банк по номеру телефона");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопки для каждого тарифа
        for (SubscriptionPlanWithPrice plan : SubscriptionPlanWithPrice.values()) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(plan.getDescription() + " - " + plan.getPrice() + " ₽");
            button.setCallbackData("pay_plan_" + plan.name());
            rows.add(Arrays.asList(button));
        }

        // Кнопка отмены
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("pay_cancel");
        rows.add(Arrays.asList(cancelButton));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }


    private void sendPaymentInstructions(Long chatId, Long paymentId, SubscriptionPlanWithPrice plan) {
        String instructions = "💳 **Инструкция по оплате:**\n\n" +
                "📱 **Номер Т-Банк:** `+79779104605`\n" +
                "💎 **Тариф:** " + plan.getDescription() + "\n" +
                "💰 **Сумма:** " + plan.getPrice() + " ₽\n" +
                "⏰ **ID платежа:** `" + paymentId + "`\n\n" +
                "**Шаги для оплаты:**\n" +
                "1. Откройте Т-Банк приложение\n" +
                "2. Выберите перевод по номеру телефона\n" +
                "3. Введите номер: `+79779104605`\n" +
                "4. Сумма: `" + plan.getPrice() + " ₽`\n" +
                "5. Укажите в комментарии: `" + paymentId + "`\n\n" +
                "После оплаты нажмите кнопку **✅ Проверить платеж**";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(instructions);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton checkButton = new InlineKeyboardButton();
        checkButton.setText("✅ Проверить платеж");
        checkButton.setCallbackData("check_payment_" + paymentId);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отменить платеж");
        cancelButton.setCallbackData("cancel_payment_" + paymentId);

        rows.add(Arrays.asList(checkButton));
        rows.add(Arrays.asList(cancelButton));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }


    public void notifyAdminAboutPaymentCheck(Long paymentId, Long userId) {
        try {
            if (adminChatId == null || adminChatId.trim().isEmpty()) {
                log.warn("Admin chat ID not configured, skipping notification");
                return;
            }

            // Получаем информацию о платеже через репозиторий
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isEmpty()) {
                log.error("Payment not found for notification: {}", paymentId);
                return;
            }

            Payment payment = paymentOpt.get();
            User user = userService.findByTelegramId(userId);

            String adminMessage = "🔄 **Новый платеж требует проверки**\n\n" +
                    "👤 **Пользователь:** " + formatUserName(user) + "\n" +
                    "📱 **Telegram ID:** " + userId + "\n" +
                    "🆔 **ID платежа:** `" + paymentId + "`\n" +
                    "💰 **Сумма:** " + payment.getAmount() + " ₽\n" +
                    "💎 **Тариф:** " + payment.getPlan().getDescription() + "\n" +
                    "⏰ **Создан:** " + payment.getCreatedAt() + "\n\n" +
                    "**Для обработки:**\n" +
                    "✅ Подтвердить: `/verify " + paymentId + " [комментарий]`\n" +
                    "❌ Отклонить: `/reject " + paymentId + " [причина]`\n\n" +
                    "Или через админ-панель в веб-приложении.";

            sendTextMessage(Long.parseLong(adminChatId), adminMessage);

            log.info("Payment check requested - ID: {}, User: {}", paymentId, userId);

        } catch (Exception e) {
            log.error("Error notifying admin about payment check", e);
        }
    }

    // Публичный метод для отправки сообщений пользователям
    public void sendTextMessageToUser(Long telegramId, String text) {
        sendTextMessage(telegramId, text);
    }

    private void handlePaymentCancel(Long chatId, Long paymentId) {
        try {
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();

                // Проверяем, что платеж принадлежит пользователю
                if (!payment.getTelegramId().equals(chatId)) {
                    sendTextMessage(chatId, "❌ Этот платеж не принадлежит вам.");
                    return;
                }

                // Обновляем статус платежа
                payment.setStatus(Payment.PaymentStatus.REJECTED);
                payment.setAdminNotes("Отменено пользователем через бота");
                paymentRepository.save(payment);

                sendTextMessage(chatId,
                        "❌ **Платеж отменен**\n\n" +
                                "ID платежа: `" + paymentId + "`\n" +
                                "Если передумаете, создайте новый платеж командой `/pay`");
            } else {
                sendTextMessage(chatId, "❌ Платеж не найден.");
            }
        } catch (Exception e) {
            log.error("Error canceling payment for user {}", chatId, e);
            sendTextMessage(chatId, "❌ Ошибка при отмене платежа.");
        }
    }

    private void handleMyPaymentsCommand(Long chatId) {
        try {
            sendTextMessage(chatId,
                    "📊 **История платежей**\n\n" +
                            "Функция истории платежей будет доступна после интеграции с PaymentService.\n\n" +
                            "Для создания платежа отправьте `/pay`");
        } catch (Exception e) {
            log.error("Error getting payment history for user {}", chatId, e);
            sendTextMessage(chatId, "❌ Ошибка при получении истории платежей.");
        }
    }

    private String getPaymentStatusText(Payment.PaymentStatus status) {
        switch (status) {
            case PENDING: return "⏳ Ожидает оплаты";
            case VERIFIED: return "✅ Подтвержден";
            case REJECTED: return "❌ Отклонен";
            case EXPIRED: return "⏰ Истек";
            default: return "❓ Неизвестно";
        }
    }

    private void handleAuthCommand(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            User user = userService.findByTelegramId(chatId);

            log.info("Starting manual auth for user: {}", chatId);

            AuthSession authSession = authSessionService.createAuthSession("manual_auth");
            String jwtToken = jwtUtil.generateToken(user.getTelegramId());
            authSessionService.completeAuthSession(authSession.getSessionId(), chatId, jwtToken);
            var subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);

            sendTextMessage(chatId,
                    "🔐 **Ручная авторизация**\n\n" +
                            "✅ Ваши данные для авторизации:\n\n" +
                            "📱 **Session ID:** `" + authSession.getSessionId() + "`\n" +
                            "📟 **Device ID:** `manual_auth`\n" +
                            "🔑 **Token:** `" + jwtToken + "`\n\n" +
                            "👤 **Пользователь:** " + formatUserName(user) + "\n" +
                            "💎 **Тариф:** " + subscription.getSubscriptionPlan().getDescription() + "\n" +
                            "📅 **Подписка до:** " + subscription.getSubscriptionEndDate() + "\n\n" +
                            "⚠️ *Сообщите эти данные в службу поддержки для завершения авторизации.*"
            );

            log.info("Manual auth completed for user: {}", chatId);

        } catch (Exception e) {
            log.warn("User not registered, cannot complete auth: {}", chatId);
            sendTextMessage(chatId,
                    "❌ **Сначала необходимо зарегистрироваться**\n\n" +
                            "Для использования сервиса:\n\n" +
                            "1. Отправьте `/register` для регистрации\n" +
                            "2. Получите 7 дней бесплатного пробного периода\n" +
                            "3. Затем используйте `/auth` для авторизации\n\n" +
                            "Или откройте приложение и следуйте инструкциям."
            );
        }
    }

    private void handleStartCommand(Long chatId, String text, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        String[] parts = text.split(" ");

        if (parts.length == 1) {
            sendWelcomeMessage(chatId);
        } else {
            String payload = parts[1];
            handleStartPayload(chatId, payload, telegramUser);
        }
    }

    private void handleStartPayload(Long chatId, String payload, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            if (payload.startsWith("auth_")) {
                handleAuthDeepLink(chatId, payload, telegramUser);
            } else if (payload.startsWith("reg_")) {
                completeRegistration(chatId, telegramUser);
            } else if (payload.startsWith("sub_")) {
                String[] parts = payload.split("_");
                if (parts.length >= 2) {
                    String plan = parts[1].toUpperCase();
                    activateSubscription(chatId, SubscriptionPlan.valueOf(plan));
                }
            } else {
                sendWelcomeMessage(chatId);
            }
        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Неверная ссылка. Отправьте /start для начала.");
            log.error("Error handling start payload: {}", payload, e);
        }
    }

    private void handleAuthDeepLink(Long chatId, String payload, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            log.info("Processing auth deep link - Payload: {}, Chat: {}", payload, chatId);

            if (!payload.startsWith("auth_")) {
                sendTextMessage(chatId, "❌ Неверный формат ссылки авторизации.");
                return;
            }

            String params = payload.substring(5);
            String sessionId;
            String deviceId = "unknown";

            int lastUnderscore = params.lastIndexOf('_');
            if (lastUnderscore != -1) {
                sessionId = params.substring(0, lastUnderscore);
                deviceId = params.substring(lastUnderscore + 1);
            } else {
                sessionId = params;
                deviceId = "telegram_bot";
            }

            log.info("Parsed - Session: {}, Device: {}, Chat: {}", sessionId, deviceId, chatId);

            Optional<AuthSession> existingSession = authSessionService.findBySessionId(sessionId);
            if (existingSession.isEmpty()) {
                sendTextMessage(chatId, "❌ Сессия авторизации не найдена или устарела.");
                return;
            }

            AuthSession session = existingSession.get();
            String actualDeviceId = "unknown".equals(deviceId) ? session.getDeviceId() : deviceId;

            if (session.getStatus() != AuthSession.AuthStatus.PENDING) {
                sendTextMessage(chatId, "❌ Сессия авторизации уже использована или отменена.");
                return;
            }

            Optional<User> userOpt = userService.findByTelegramIdOptional(chatId);
            if (userOpt.isPresent()) {
                log.info("User found, proceeding with auth: {}", chatId);
                handleExistingUserAuth(chatId, userOpt.get(), sessionId, actualDeviceId);
                return;
            }

            log.info("User not registered, registering and completing auth: {}", chatId);
            completeRegistrationWithAuth(chatId, telegramUser, sessionId, actualDeviceId);

        } catch (Exception e) {
            log.error("Auth deep link error for user {}", chatId, e);
            sendTextMessage(chatId, "❌ Ошибка обработки ссылки авторизации.");
        }
    }

    private void handleExistingUserAuth(Long chatId, User user, String sessionId, String deviceId) {
        try {
            String jwtToken = jwtUtil.generateToken(user.getTelegramId());
            log.info("Generated JWT token for user: {}, session: {}", chatId, sessionId);

            AuthSession completedSession = authSessionService.completeAuthSession(sessionId, chatId, jwtToken);

            if (completedSession == null || completedSession.getStatus() != AuthSession.AuthStatus.COMPLETED) {
                sendTextMessage(chatId, "❌ Ошибка завершения сессии авторизации.");
                log.error("Failed to complete auth session: {}", sessionId);
                return;
            }

            var subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);
            boolean isSubscriptionActive = telegramAuthService.isSubscriptionActive(subscription);
            String planLabel = subscription.getSubscriptionPlan() != null
                    ? subscription.getSubscriptionPlan().getDescription()
                    : "Не указан";
            String subscriptionInfo = isSubscriptionActive
                    ? "✅ **Авторизация прошла успешно!**\n\n"
                    + "👤 **Имя:** " + formatUserName(user) + "\n"
                    + "💎 **Тариф:** " + planLabel + "\n"
                    + "📅 **Подписка до:** " + subscription.getSubscriptionEndDate() + "\n"
                    + "⏱ **Осталось дней:** " + telegramAuthService.getDaysRemaining(subscription) + "\n\n"
                    + "🔐 **Вы можете вернуться в приложение.**\n"
                    + "Авторизация произойдет автоматически."
                    : "⚠️ **Подписка не активна**\n\n"
                    + "Ваша подписка истекла " + subscription.getSubscriptionEndDate() + "\n"
                    + "Для продления подписки отправьте `/pay`\n\n"
                    + "🔐 **Вы можете вернуться в приложение.**\n"
                    + "Авторизация произойдет автоматически.";

            sendTextMessage(chatId, subscriptionInfo);

            log.info("User authenticated via deep link: {}, session: {}", chatId, sessionId);

        } catch (Exception e) {
            log.error("Error in handleExistingUserAuth for user: {}, session: {}", chatId, sessionId, e);
            sendTextMessage(chatId, "❌ Внутренняя ошибка при авторизации.");
        }
    }

    private void sendRegistrationOfferWithAuth(Long chatId, String sessionId, String deviceId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            String safeDeviceId = deviceId.replace("_", "-");

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🎉 **Добро пожаловать!**\n\n" +
                    "Для использования приложения необходимо зарегистрироваться.\n\n" +
                    "✅ **После регистрации вы получите:**\n" +
                    "• 7 дней бесплатного пробного периода\n" +
                    "• Полный доступ ко всем функциям\n" +
                    "• Автоматическую авторизацию в приложении");

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            InlineKeyboardButton registerButton = new InlineKeyboardButton();
            registerButton.setText("🚀 Зарегистрироваться");
            registerButton.setCallbackData("register_auth_" + sessionId + "_" + safeDeviceId);

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(registerButton);
            rows.add(row1);
            keyboard.setKeyboard(rows);
            message.setReplyMarkup(keyboard);

            executeMessage(message);

            log.info("Registration offer sent with auth - Session: {}, Device: {}, Chat: {}", sessionId, deviceId, chatId);

        } catch (Exception e) {
            log.error("Error sending registration offer: {}", e.getMessage());
            sendTextMessage(chatId, "❌ Ошибка при создании формы регистрации.");
        }
    }

    private void handleRegisterCommand(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            User existingUser = userService.findByTelegramId(chatId);
            sendSubscriptionInfo(chatId, existingUser);
        } catch (Exception e) {
            sendRegistrationOffer(chatId);
        }
    }

    private void sendRegistrationOffer(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🎉 **Добро пожаловать в сервис подписок!**\n\n" +
                "Получите 7 дней бесплатного пробного периода с полным доступом ко всем функциям.\n\n" +
                "Нажмите кнопку ниже для регистрации:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton registerButton = new InlineKeyboardButton();
        registerButton.setText("🚀 Зарегистрироваться");
        registerButton.setCallbackData("register_confirm");

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("register_cancel");

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(registerButton);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(cancelButton);

        rows.add(row1);
        rows.add(row2);
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private void completeRegistration(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            User user = createUser(chatId, telegramUser);
            var subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);

            sendTextMessage(chatId,
                    "🎉 **Регистрация завершена!**\n\n" +
                            "✅ Вы успешно зарегистрированы в сервисе\n\n" +
                            "📅 **Бесплатный период до:** " + subscription.getSubscriptionEndDate() + "\n" +
                            "⏱ **Осталось дней:** 7\n" +
                            "💎 **Тариф:** " + subscription.getSubscriptionPlan().getDescription() + "\n\n" +
                            "🔐 **Для авторизации в приложении:**\n" +
                            "• Вернитесь в приложение и нажмите 'Проверить авторизацию'\n" +
                            "• Или отправьте `/auth` в этом чате для ручной авторизации"
            );

            log.info("User registered successfully: {}", chatId);

        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка регистрации: " + e.getMessage());
            log.error("Registration error for user {}", chatId, e);
        }
    }

    private void completeRegistrationWithAuth(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser, String sessionId, String deviceId) {
        try {
            log.info("Starting registration with auth - Session: {}, Device: {}, Chat: {}", sessionId, deviceId, chatId);

            User user = createUser(chatId, telegramUser);
            var subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);

            String jwtToken = jwtUtil.generateToken(user.getTelegramId());
            AuthSession completedSession = authSessionService.completeAuthSession(sessionId, deviceId, chatId, jwtToken);

            sendTextMessage(chatId,
                    "🎉 **Регистрация и авторизация завершены!**\n\n" +
                            "✅ Вы успешно зарегистрированы\n" +
                            "📅 **Бесплатный период:** 7 дней\n" +
                            "💎 **Тариф:** " + subscription.getSubscriptionPlan().getDescription() + "\n\n" +
                            "🔐 **Вы можете вернуться в приложение.**\n" +
                            "Авторизация произойдет автоматически."
            );

            log.info("User registered and authenticated: {}, session: {}", chatId, sessionId);

        } catch (Exception e) {
            log.error("Registration error for user {}: {}", chatId, e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ошибка регистрации: " + e.getMessage());
        }
    }

    private User createUser(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        return userService.createUser(
                chatId,
                telegramUser.getFirstName(),
                telegramUser.getLastName(),
                telegramUser.getUserName()
        );
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            if (data.startsWith("register_auth_")) {
                // ... существующий код ...
            } else if (data.equals("register_confirm")) {
                completeRegistration(chatId, callbackQuery.getFrom());
            } else if (data.equals("register_cancel")) {
                editMessageText(chatId, messageId, "❌ Регистрация отменена.\n\nОтправьте `/register` если передумаете.");
            } else if (data.startsWith("pay_plan_")) {
                String planName = data.substring("pay_plan_".length());
                handlePaymentPlanSelection(chatId, planName, callbackQuery.getFrom());
            } else if (data.startsWith("check_payment_")) {
                Long paymentId = Long.parseLong(data.substring("check_payment_".length()));
                handlePaymentCheck(chatId, paymentId);
            } else if (data.startsWith("cancel_payment_")) {
                Long paymentId = Long.parseLong(data.substring("cancel_payment_".length()));
                handlePaymentCancel(chatId, paymentId);
            } else if (data.equals("pay_cancel")) {
                editMessageText(chatId, messageId, "❌ Выбор тарифа отменен.");
            } else if (data.equals("profile_login")) {
                loginWaitingUsers.add(chatId);
                passwordWaitingUsers.remove(chatId);
                editMessageText(chatId, messageId,
                        "Введите логин (латиница, цифры, точка, дефис, подчёркивание). " +
                                "Для отмены отправьте /cancel");
            } else if (data.equals("profile_password")) {
                passwordWaitingUsers.add(chatId);
                loginWaitingUsers.remove(chatId);
                editMessageText(chatId, messageId,
                        "Введите новый пароль (минимум 6 символов). Для отмены отправьте /cancel");
            } else if (data.equals("profile_cancel")) {
                loginWaitingUsers.remove(chatId);
                passwordWaitingUsers.remove(chatId);
                editMessageText(chatId, messageId, "Изменение профиля отменено.");
            }

            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            executeMethod(answer);

        } catch (Exception e) {
            log.error("Error handling callback", e);
        }
    }

    public AuthSessionService.AuthStatusResponse checkAuthStatus(String sessionId, String deviceId) {
        return authSessionService.checkAuthStatus(sessionId, deviceId);
    }

    public Map<String, Object> getDebugInfo() {
        return authSessionService.getDebugInfo();
    }

    public String generateAuthDeepLink(String sessionId, String deviceId) {
        if (deviceId == null || deviceId.isEmpty() || "unknown".equals(deviceId)) {
            return "https://t.me/" + botUsername + "?start=auth_" + sessionId;
        } else {
            String safeDeviceId = deviceId.replace("_", "-");
            return "https://t.me/" + botUsername + "?start=auth_" + sessionId + "_" + safeDeviceId;
        }
    }

    private void handleStatusCommand(Long chatId) {
        try {
            User user = userService.findByTelegramId(chatId);
            sendSubscriptionInfo(chatId, user);
        } catch (Exception e) {
            sendTextMessage(chatId,
                    "❌ **Вы еще не зарегистрированы**\n\n" +
                            "Для регистрации отправьте:\n" +
                            "`/register` - начать регистрацию\n" +
                            "`/help` - помощь"
            );
        }
    }

    private void sendSubscriptionInfo(Long chatId, User user) {
        var subscription = userService.getOrCreateService(user, ServiceCode.VACANCY);
        boolean isActive = telegramAuthService.isSubscriptionActive(subscription);
        int daysRemaining = telegramAuthService.getDaysRemaining(subscription);

        String status = isActive ? "✅ Активна" : "❌ Неактивна";
        String daysText = isActive ? "⏱ Осталось дней: " + daysRemaining : "⏱ Подписка истекла";

        String message = "📊 **Ваша подписка:**\n\n" +
                "👤 **Пользователь:** " + formatUserName(user) + "\n" +
                "📅 **Окончание:** " + subscription.getSubscriptionEndDate() + "\n" +
                daysText + "\n" +
                "💎 **Статус:** " + status + "\n" +
                "🏷 **Тариф:** " + subscription.getSubscriptionPlan().getDescription() + "\n\n" +
                "💳 Для продления отправьте `/pay`";

        sendTextMessage(chatId, message);
    }

    private String formatUserName(User user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        } else if (user.getFirstName() != null) {
            return user.getFirstName();
        } else if (user.getUsername() != null) {
            return "@" + user.getUsername();
        } else {
            return "ID: " + user.getTelegramId();
        }
    }

    private void handleHelpCommand(Long chatId) {
        String helpText = "ℹ️ **Помощь по боту:**\n\n" +
                "`/start` - начать работу с ботом\n" +
                "`/register` - регистрация в сервисе\n" +
                "`/auth` - ручная авторизация в приложении\n" +
                "`/profile` - установить логин/пароль\n" +
                "`/pay` - оплата подписки\n" +
                "`/my_payments` - история платежей\n" +
                "`/status` - статус вашей подписки\n" +
                "`/support` - написать в поддержку\n" +
                "`/help` - показать эту справку\n\n" +
                "💳 **Оплата:** через Т-Банк по номеру +79779104605\n" +
                "📞 **Поддержка:** contact@yourdomain.com";

        sendTextMessage(chatId, helpText);
    }

    public AuthSession createAuthSession(String deviceId) {
        return authSessionService.createAuthSession(deviceId);
    }

    private void handleAdminCommand(Long chatId) {
        try {
            if (isAdmin(chatId)) {
                sendAdminPanel(chatId);
            } else {
                sendTextMessage(chatId, "❌ У вас нет доступа к админ-панели.");
            }
        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка доступа.");
        }
    }

    private boolean isAdmin(Long chatId) {
        return adminChatId != null && !adminChatId.isEmpty() && adminChatId.equals(chatId.toString());
    }

    private void sendAdminPanel(Long chatId) {
        try {
            long totalUsers = userService.getAllUsers().size();
            long activeSubscriptions = userService.getActiveSubscriptions(ServiceCode.VACANCY).size();
            long expiredSubscriptions = userService.getExpiredSubscriptions(ServiceCode.VACANCY).size();

            String stats = "👑 **Админ-панель**\n\n" +
                    "📊 **Статистика:**\n" +
                    "• Всего пользователей: " + totalUsers + "\n" +
                    "• Активных подписок: " + activeSubscriptions + "\n" +
                    "• Истекших подписок: " + expiredSubscriptions + "\n\n" +
                    "💳 **Команды для обработки платежей:**\n" +
                    "✅ `/verify <ID> [комментарий]` - подтвердить платеж\n" +
                    "❌ `/reject <ID> <причина>` - отклонить платеж\n\n" +
                    "🌐 **API endpoints:**\n" +
                    "GET /api/admin/payments/pending\n" +
                    "POST /api/admin/payments/{id}/verify\n" +
                    "POST /api/admin/payments/{id}/reject";

            sendTextMessage(chatId, stats);
        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка загрузки статистики.");
        }
    }

    private void handleUnknownCommand(Long chatId) {
        sendTextMessage(chatId,
                "🤔 **Неизвестная команда**\n\n" +
                        "Используйте:\n" +
                        "`/start` - начать работу\n" +
                        "`/support` - написать в поддержку\n" +
                        "`/help` - помощь"
        );
    }

    private void handleSupportCommand(Long chatId) {
        supportWaitingUsers.add(chatId);
        sendTextMessage(chatId,
                "🆘 **Поддержка**\n\n" +
                        "Напишите ваше сообщение одним текстом. " +
                        "Чтобы отменить, отправьте `/cancel`."
        );
    }

    private void handleSupportMessage(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser, String text) {
        supportWaitingUsers.remove(chatId);
        if (text == null || text.trim().isEmpty()) {
            sendTextMessage(chatId, "Сообщение пустое. Попробуйте снова: /support");
            return;
        }

        SupportMessage supportMessage = supportMessageService.createMessage(chatId, text.trim(), "BOT");
        notifyAdminAboutSupport(supportMessage, telegramUser);

        sendTextMessage(chatId,
                "✅ Сообщение отправлено в поддержку.\n" +
                        "Мы постараемся ответить как можно быстрее."
        );
    }

    private void handleProfileCommand(Long chatId) {
        try {
            userService.findByTelegramId(chatId);
        } catch (Exception e) {
            sendTextMessage(chatId,
                    "❌ **Сначала необходимо зарегистрироваться**\n\n" +
                            "Отправьте `/register` для регистрации.");
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("👤 **Профиль**\n\nВыберите, что хотите изменить:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton loginButton = new InlineKeyboardButton();
        loginButton.setText("🔑 Установить логин");
        loginButton.setCallbackData("profile_login");

        InlineKeyboardButton passwordButton = new InlineKeyboardButton();
        passwordButton.setText("🔐 Установить пароль");
        passwordButton.setCallbackData("profile_password");

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("profile_cancel");

        rows.add(Arrays.asList(loginButton));
        rows.add(Arrays.asList(passwordButton));
        rows.add(Arrays.asList(cancelButton));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private void handleLoginUpdate(Long chatId, String login) {
        loginWaitingUsers.remove(chatId);
        try {
            userService.updateLogin(chatId, login);
            sendTextMessage(chatId, "✅ Логин установлен.");
        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handlePasswordUpdate(Long chatId, String password) {
        passwordWaitingUsers.remove(chatId);
        try {
            userService.updatePassword(chatId, password);
            sendTextMessage(chatId, "✅ Пароль установлен.");
        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    public void notifyAdminAboutSupport(SupportMessage message, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            if (adminChatId == null || adminChatId.trim().isEmpty()) {
                log.warn("Admin chat ID not configured, skipping support notification");
                return;
            }

            String userLine = telegramUser != null
                    ? (telegramUser.getFirstName() != null ? telegramUser.getFirstName() : "") +
                    (telegramUser.getLastName() != null ? " " + telegramUser.getLastName() : "")
                    : "Пользователь";

            String adminMessage = "🆘 **Новое обращение в поддержку**\n\n" +
                    "👤 **Пользователь:** " + userLine + "\n" +
                    "🆔 **Telegram ID:** " + message.getTelegramId() + "\n" +
                    "🕒 **Время:** " + message.getCreatedAt() + "\n\n" +
                    "💬 **Сообщение:**\n" + message.getMessage();

            sendTextMessage(Long.parseLong(adminChatId), adminMessage);
        } catch (Exception e) {
            log.error("Error notifying admin about support message", e);
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        String welcomeText = "👋 **Привет! Я бот для управления подписками.**\n\n" +
                "С моей помощью вы можете:\n" +
                "• Зарегистрироваться в сервисе\n" +
                "• Получить пробный период\n" +
                "• Управлять своей подпиской\n" +
                "• Оплатить подписку через Т-Банк\n" +
                "• Авторизоваться в приложении\n\n" +
                "**Для начала отправьте:**\n" +
                "`/register` - регистрация\n" +
                "`/pay` - оплата подписки\n" +
                "`/auth` - авторизация в приложении\n" +
                "`/help` - помощь";

        sendTextMessage(chatId, welcomeText);
    }

    private void activateSubscription(Long chatId, SubscriptionPlan plan) {
        try {
            User user = userService.findByTelegramId(chatId);
            userService.extendSubscription(chatId, plan.getDays(), plan, ServiceCode.VACANCY);
            sendTextMessage(chatId, "✅ Подписка активирована: " + plan.getDescription());
        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка активации подписки.");
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        // Логируем исходящее сообщение
        botMessageService.logMessage(chatId, "TEXT", text, "OUT");
        try {
            log.info("Отправка сообщения Telegram: chatId={} text={}", chatId, text);
            executeMessage(message);
            log.info("Успешно отправлено");
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения Telegram пользователю " + chatId, e);
            throw e; // обязательно пробрасываем, чтобы контроллер вернул 500 (или кастомно обработать)
        }
    }


    private void editMessageText(Long chatId, Integer messageId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode("Markdown");
        executeMethod(message);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message", e);
            throw new RuntimeException("Telegram API error: " + e.getMessage(), e);
        }
    }

    private void executeMethod(org.telegram.telegrambots.meta.api.methods.BotApiMethod<?> method) {
        try {
            execute(method);
        } catch (TelegramApiException e) {
            log.error("Error executing method", e);
        }
    }

    public String generateRegistrationLink() {
        return "https://t.me/" + botUsername + "?start=reg_" + System.currentTimeMillis();
    }

    public String generateSubscriptionLink(SubscriptionPlan plan) {
        return "https://t.me/" + botUsername + "?start=sub_" + plan.name().toLowerCase();
    }
    private void handlePaymentPlanSelection(Long chatId, String planName, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            SubscriptionPlanWithPrice plan = SubscriptionPlanWithPrice.valueOf(planName);

            // Получаем пользователя
            User user = userService.findByTelegramId(chatId);

            // Создаем платеж через репозиторий напрямую
            Payment payment = new Payment(chatId, plan.getPrice(), plan.toSubscriptionPlan(), plan.getMonths(), ServiceCode.VACANCY);
            Payment savedPayment = paymentRepository.save(payment);

            sendPaymentInstructions(chatId, savedPayment.getId(), plan);

        } catch (Exception e) {
            log.error("Error creating payment for user {}", chatId, e);
            sendTextMessage(chatId, "❌ Ошибка при создании платежа: " + e.getMessage());
        }
    }

    private void handlePaymentCheck(Long chatId, Long paymentId) {
        try {
            // Проверяем существование платежа
            Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isEmpty()) {
                sendTextMessage(chatId, "❌ Платеж не найден.");
                return;
            }

            Payment payment = paymentOpt.get();

            // Проверяем, что платеж принадлежит пользователю
            if (!payment.getTelegramId().equals(chatId)) {
                sendTextMessage(chatId, "❌ Этот платеж не принадлежит вам.");
                return;
            }

            // Проверяем статус платежа
            if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
                sendTextMessage(chatId,
                        "ℹ️ Статус платежа: " + getPaymentStatusText(payment.getStatus()) + "\n" +
                                "Комментарий: " + (payment.getAdminNotes() != null ? payment.getAdminNotes() : "отсутствует")
                );
                return;
            }

            // Уведомляем администратора через событие
            applicationEventPublisher.publishEvent(new PaymentNotificationEvent(this, paymentId, chatId));

            sendTextMessage(chatId,
                    "✅ **Уведомление отправлено администратору**\n\n" +
                            "ID платежа: `" + paymentId + "`\n" +
                            "💰 Сумма: " + payment.getAmount() + " ₽\n" +
                            "💎 Тариф: " + payment.getPlan().getDescription() + "\n" +
                            "⏳ Ожидайте проверки платежа администратором.\n" +
                            "Обычно проверка занимает до 24 часов.\n\n" +
                            "Вы получите уведомление когда платеж будет подтвержден."
            );

        } catch (Exception e) {
            log.error("Error checking payment for user {}", chatId, e);
            sendTextMessage(chatId, "❌ Ошибка при проверке платежа.");
        }
    }

}
