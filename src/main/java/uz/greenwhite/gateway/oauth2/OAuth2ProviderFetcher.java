package uz.greenwhite.gateway.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.greenwhite.gateway.config.GatewayProperties;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;
import uz.greenwhite.gateway.util.AuthUtil;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class OAuth2ProviderFetcher {

    private static final String CACHE_PREFIX = "oauth2:provider:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient;
    private final GatewayProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OAuth2ProviderFetcher(GatewayProperties properties,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * Fetch provider credentials: Redis cache → Oracle HTTP fallback
     */
    public ProviderProperties fetch(String providerName, Long companyId) {
        ProviderProperties cached = getFromCache(providerName);
        if (cached != null) {
            log.debug("OAuth2 provider '{}' found in cache", providerName);
            return cached;
        }

        ProviderProperties fetched = fetchFromOracle(providerName, companyId);
        if (fetched == null) {
            throw new RuntimeException("OAuth2 provider not found in Oracle: " + providerName);
        }

        saveToCache(providerName, fetched);
        log.info("OAuth2 provider '{}' fetched from Oracle and cached", providerName);

        return fetched;
    }

    /**
     * Evict cached provider credentials (e.g., when credentials are updated)
     */
    public void evictCache(String providerName) {
        redisTemplate.delete(CACHE_PREFIX + providerName);
        log.info("OAuth2 provider cache evicted: {}", providerName);
    }

    private ProviderProperties fetchFromOracle(String providerName, Long companyId) {
        try {
            Map<String, Object> body = Map.of(
                    "company_id", companyId,
                    "oauth2_provider", providerName
            );

            String response = restClient.post()
                    .uri(properties.getOauth2ProviderUri())
                    .header(HttpHeaders.AUTHORIZATION,
                            AuthUtil.generateBasicAuth(properties.getUsername(), properties.getPassword()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return null;
            }

            ProviderProperties props = objectMapper.readValue(response, ProviderProperties.class);

            if (props.getTokenUrl() == null || props.getClientId() == null) {
                log.warn("OAuth2 provider '{}' returned incomplete data from Oracle", providerName);
                return null;
            }

            return props;

        } catch (Exception e) {
            log.error("Failed to fetch OAuth2 provider '{}' from Oracle: {}", providerName, e.getMessage(), e);
            throw new RuntimeException("Oracle OAuth2 provider fetch failed: " + e.getMessage(), e);
        }
    }

    private ProviderProperties getFromCache(String providerName) {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_PREFIX + providerName);
            if (json == null) return null;
            return objectMapper.readValue(json, ProviderProperties.class);
        } catch (Exception e) {
            log.warn("Failed to read OAuth2 provider from cache: {}", e.getMessage());
            return null;
        }
    }

    private void saveToCache(String providerName, ProviderProperties props) {
        try {
            String json = objectMapper.writeValueAsString(props);
            redisTemplate.opsForValue().set(CACHE_PREFIX + providerName, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache OAuth2 provider: {}", e.getMessage());
        }
    }
}