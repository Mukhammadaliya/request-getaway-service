package uz.greenwhite.gateway.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.greenwhite.gateway.oauth2.client.OAuth2Client;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;
import uz.greenwhite.gateway.oauth2.model.Token;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2TokenRedisCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_KEY_PREFIX = "oauth2:token:";
    private static final String LOCK_KEY_PREFIX = "oauth2:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final long MARGIN_MS = 15_000;

    /**
     * Getting token: Redis cache → refresh expired → get new if not exists
     */
    public Token getToken(String providerName, ProviderProperties properties, OAuth2Client client) {
        // 1. Getting from Redis
        Token cached = getFromRedis(providerName);

        if (cached != null && !cached.isExpired()) {
            log.debug("OAuth2 token cache HIT: {}", providerName);
            return cached;
        }

        // 2. Getting from Lock — only one instance will refresh
        if (!acquireLock(providerName)) {
            log.debug("Another instance is refreshing token: {}, waiting...", providerName);
            return waitForToken(providerName, cached);
        }

        try {
            // 3. Double-check — check after getting lock
            Token doubleCheck = getFromRedis(providerName);
            if (doubleCheck != null && !doubleCheck.isExpired()) {
                log.debug("OAuth2 token refreshed by another instance: {}", providerName);
                return doubleCheck;
            }

            // 4. Getting toke or refresh
            Token newToken;
            if (cached != null && cached.refreshToken() != null) {
                log.info("Refreshing OAuth2 token: {}", providerName);
                try {
                    newToken = client.refreshAccessToken(properties, cached);
                } catch (Exception e) {
                    log.warn("Token refresh failed for {}, getting new token: {}", providerName, e.getMessage());
                    newToken = client.getAccessToken(properties);
                }
            } else {
                log.info("Getting new OAuth2 token: {}", providerName);
                newToken = client.getAccessToken(properties);
            }

            // 5. Save to Redis
            saveToRedis(providerName, newToken);
            log.info("OAuth2 token saved to Redis: {}", providerName);

            return newToken;

        } catch (Exception e) {
            log.error("Failed to get/refresh OAuth2 token: {} - {}", providerName, e.getMessage());
            // If old token exists in cache — return it as fallback (even if expired)
            if (cached != null) {
                log.warn("Returning expired token as fallback: {}", providerName);
                return cached;
            }
            throw new RuntimeException("Failed to get OAuth2 token for: " + providerName, e);
        } finally {
            releaseLock(providerName);
        }
    }

    // ==================== REDIS OPERATIONS ====================

    private Token getFromRedis(String providerName) {
        try {
            String json = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + providerName);
            if (json == null) return null;
            return objectMapper.readValue(json, Token.class);
        } catch (Exception e) {
            log.warn("Failed to read token from Redis: {} - {}", providerName, e.getMessage());
            return null;
        }
    }

    private void saveToRedis(String providerName, Token token) {
        try {
            String json = objectMapper.writeValueAsString(token);
            long ttlMs = token.expiresIn() - MARGIN_MS;
            if (ttlMs > 0) {
                redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + providerName, json, Duration.ofMillis(ttlMs));
            } else {
                redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + providerName, json, Duration.ofSeconds(30));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to save token to Redis: {} - {}", providerName, e.getMessage());
        }
    }

    // ==================== DISTRIBUTED LOCK ====================

    private boolean acquireLock(String providerName) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY_PREFIX + providerName, "locked", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseLock(String providerName) {
        redisTemplate.delete(LOCK_KEY_PREFIX + providerName);
    }

    /**
     * Waiting when another instance token refreshing, getting from Redis
     */
    private Token waitForToken(String providerName, Token fallback) {
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(200);
                Token token = getFromRedis(providerName);
                if (token != null && !token.isExpired()) {
                    return token;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Timeout — return fallback
        if (fallback != null) {
            log.warn("Wait timeout, returning fallback token: {}", providerName);
            return fallback;
        }
        throw new RuntimeException("Failed to get OAuth2 token after waiting: " + providerName);
    }
}