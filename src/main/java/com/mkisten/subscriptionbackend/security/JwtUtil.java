package com.mkisten.subscriptionbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret:defaultSecretKeyThatIsAtLeast32CharactersLongForTelegramAuth}")
    private String secret;

    @Value("${jwt.expiration:604800000}") // 7 дней в миллисекундах
    private Long expiration;

    @Value("${jwt.clock-skew:30000}") // 30 секунд допуск на рассинхронизацию времени
    private Long clockSkew;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            return Keys.hmacShaKeyFor(paddedKey);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Генерация токена для Telegram пользователя
     */
    public String generateToken(Long telegramId) {
        Date issuedAt = new Date();
        Date expiration = new Date(System.currentTimeMillis() + this.expiration);

        log.info("Generating JWT token - User: {}, Issued: {}, Expires: {}, Expiration ms: {}",
                telegramId, issuedAt, expiration, this.expiration);

        return Jwts.builder()
                .setSubject(telegramId.toString())
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Генерация токена с дополнительными claims
     */
    public String generateToken(Long telegramId, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>(additionalClaims);
        claims.put("telegramId", telegramId);
        claims.put("type", "telegram");
        return createToken(claims, telegramId.toString());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Извлечение Telegram ID из токена
     */
    public Long extractTelegramId(String token) {
        try {
            String subject = extractClaim(token, Claims::getSubject);
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            log.error("Invalid telegram ID in token: {}", token);
            throw new RuntimeException("Invalid telegram ID in token");
        } catch (Exception e) {
            log.error("Error extracting telegram ID from token: {}", e.getMessage());
            throw new RuntimeException("Invalid token");
        }
    }

    /**
     * Извлечение username (для обратной совместимости)
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Извлечение всех claims с обработкой ошибок
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(clockSkew / 1000) // Добавляем допуск по времени
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            throw e; // Пробрасываем дальше для правильной обработки
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid token format");
        } catch (io.jsonwebtoken.SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new RuntimeException("Invalid token signature");
        } catch (Exception e) {
            log.error("Error parsing JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid token");
        }
    }

    /**
     * Проверка истечения токена
     */
    public Boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            Date now = new Date(System.currentTimeMillis() + clockSkew); // Учитываем clock skew
            return expiration.before(now);
        } catch (Exception e) {
            log.warn("Token expiration check failed: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Основная валидация токена
     */
    public boolean validateToken(String token) {
        try {
            // Пытаемся распарсить токен
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(clockSkew / 1000)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Проверяем expiration
            Date expiration = claims.getExpiration();
            Date now = new Date();
            boolean isValid = expiration.after(new Date(now.getTime() - clockSkew)); // Учитываем clock skew

            log.debug("Token validation - Expires: {}, Now: {}, Valid: {}",
                    expiration, now, isValid);

            return isValid;

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Валидация токена для конкретного пользователя
     */
    public Boolean validateToken(String token, Long telegramId) {
        try {
            if (!validateToken(token)) {
                return false;
            }

            final Long tokenTelegramId = extractTelegramId(token);
            return tokenTelegramId.equals(telegramId);

        } catch (Exception e) {
            log.error("Token validation for user failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Валидация токена для обратной совместимости
     */
    public Boolean validateToken(String token, String username) {
        try {
            if (!validateToken(token)) {
                return false;
            }

            final String tokenUsername = extractUsername(token);
            return tokenUsername.equals(username);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получение оставшегося времени жизни токена
     */
    public Long getRemainingTimeMillis(String token) {
        try {
            Date expiration = extractExpiration(token);
            return Math.max(0, expiration.getTime() - System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Cannot get remaining time for token: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Проверка, является ли токен Telegram-токеном
     */
    public Boolean isTelegramToken(String token) {
        try {
            String type = extractCustomClaim(token, "type", String.class);
            return "telegram".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Извлечение конкретного claim
     */
    public <T> T extractCustomClaim(String token, String claimName, Class<T> requiredType) {
        try {
            final Claims claims = extractAllClaims(token);
            return claims.get(claimName, requiredType);
        } catch (Exception e) {
            log.warn("Cannot extract claim {} from token: {}", claimName, e.getMessage());
            return null;
        }
    }

    /**
     * Обновление токена
     */
    public String refreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Long telegramId = extractTelegramId(token);

            // Удаляем временные claims
            claims.remove("iat");
            claims.remove("exp");

            return createToken(claims, telegramId.toString());
        } catch (Exception e) {
            log.error("Cannot refresh token: {}", e.getMessage());
            throw new RuntimeException("Cannot refresh token");
        }
    }
}