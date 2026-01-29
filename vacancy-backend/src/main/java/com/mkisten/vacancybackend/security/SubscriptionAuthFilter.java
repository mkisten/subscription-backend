package com.mkisten.vacancybackend.security;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.SubscriptionStatusResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionAuthFilter extends OncePerRequestFilter {

    private final AuthServiceClient authServiceClient;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI(); // с учётом context-path, т.е. /api/....

        // 1. Публичные эндпоинты – фильтр пропускает без проверок
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Достаём токен
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response,
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Missing or invalid Authorization header");
            return;
        }

        String token = header.substring(7);

        try {
            // 3. Валидация токена через auth‑сервис
            boolean isValid = authServiceClient.validateToken(token);
            if (!isValid) {
                writeError(response,
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN",
                        "JWT token is invalid or expired");
                return;
            }

            // 4. Статус подписки через auth‑сервис
            SubscriptionStatusResponse subStatus =
                    authServiceClient.getSubscriptionStatus(token);

            if (subStatus == null || Boolean.FALSE.equals(subStatus.getActive())) {
                // Можно различать EXPIRED / TRIAL_ONLY и т.д., если в DTO есть поля
                writeError(response,
                        HttpStatus.FORBIDDEN,
                        "SUBSCRIPTION_INACTIVE",
                        "Subscription is not active");
                return;
            }

            // 5. Кладём полезные данные в request, чтобы контроллеры могли их читать
            request.setAttribute("telegramId", subStatus.getTelegramId());
            request.setAttribute("subscriptionStatus", subStatus);

            filterChain.doFilter(request, response);

        } catch (HttpStatusCodeException e) {
            // auth‑сервис вернул 4xx/5xx – пробрасываем его ответ как есть
            log.warn("Auth service HTTP error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            response.setStatus(e.getStatusCode().value());
            response.setContentType("application/json");
            response.getWriter().write(e.getResponseBodyAsString());
        } catch (Exception e) {
            // Сетевая ошибка, timeout и т.п.
            log.error("Auth service error", e);
            writeError(response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SERVICE_UNAVAILABLE",
                    "Authorization service is temporarily unavailable");
        }
    }

    /**
     * Здесь список эндпоинтов, которые должны работать без авторизации.
     * ВНИМАНИЕ: path включает context-path (/api), потому что берём getRequestURI().
     */
    private boolean isPublicEndpoint(String path) {
        return
                // Telegram WebApp авторизация (создание сессии)
                path.startsWith("/api/telegram-auth") ||

                        // Прокси-эндпоинты авторизации, которые сами ходят в auth‑сервис:
                        path.startsWith("/api/auth/token") ||        // получить токен по telegramId
                        path.startsWith("/api/auth/validate") ||     // опционально — если хочешь без фильтра
                        path.startsWith("/api/auth/token-info") ||

                        // technical / infra
                        path.startsWith("/api/test") ||
                        path.startsWith("/actuator") ||
                        path.startsWith("/health") ||
                        path.startsWith("/swagger-ui") ||
                        path.startsWith("/v3/api-docs") ||
                        path.startsWith("/swagger-resources") ||
                        path.startsWith("/webjars") ||
                        path.equals("/favicon.ico") ||
                        path.equals("/error");
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");

        // Простой единый формат, см. ниже раздел про JSON ошибок
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"code\":\"%s\",\"message\":\"%s\"}",
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                // чтобы кавычки не ломали JSON
                message.replace("\"", "'")
        );
        response.getWriter().write(body);
    }
}
