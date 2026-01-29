package com.mkisten.subscriptionbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getServletPath();
        String requestMethod = request.getMethod();

        log.debug("Processing {} request for path: {}", requestMethod, requestPath);

        // Пропускаем публичные эндпоинты
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Для OPTIONS запросов пропускаем аутентификацию
        if ("OPTIONS".equalsIgnoreCase(requestMethod)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            sendError(response, "Missing or invalid Authorization header");
            return;
        }

        String jwt = authorizationHeader.substring(7);

        try {
            Long telegramId = jwtUtil.extractTelegramId(jwt);

            if (!jwtUtil.validateToken(jwt)) {
                sendError(response, "Invalid token");
                return;
            }

            User user = userService.findByTelegramId(telegramId);

            if (!jwtUtil.validateToken(jwt, telegramId)) {
                sendError(response, "Token user mismatch");
                return;
            }

            // Создаем аутентификацию с authorities
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authenticated user: {} with role: {}", user.getTelegramId(), user.getRole());

        } catch (Exception e) {
            sendError(response, "Authentication failed: " + e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        log.warn("Authentication error: {}", message);
//        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//        response.setContentType("application/json");
//        response.getWriter().write("{\"error\": \"UNAUTHORIZED\", \"message\": \"" + message + "\"}");
        HttpStatus status = HttpStatus.UNAUTHORIZED;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", "UNAUTHORIZED");
        body.put("message", message);

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/token") ||
                path.startsWith("/api/telegram-auth") ||
                path.startsWith("/api/test") ||
                path.startsWith("/actuator") ||
                path.startsWith("/health") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/webjars") ||
                path.startsWith("/swagger-resources") ||
                path.startsWith("/configuration") ||
                path.equals("/favicon.ico") ||
                path.equals("/error");
    }
}
