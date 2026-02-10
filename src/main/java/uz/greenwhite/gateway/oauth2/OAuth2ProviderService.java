package uz.greenwhite.gateway.oauth2;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.oauth2.client.OAuth2Client;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;
import uz.greenwhite.gateway.oauth2.model.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OAuth2ProviderService {

    private final OAuth2Config oAuth2Config;
    private final OAuth2TokenRedisCache tokenRedisCache;
    private final Map<String, OAuth2Client> oAuth2ClientMap;

    @Getter
    private final Map<String, Object> information = new HashMap<>();

    public OAuth2ProviderService(OAuth2Config oAuth2Config,
                                 OAuth2TokenRedisCache tokenRedisCache,
                                 List<OAuth2Client> oAuth2Clients) {
        this.oAuth2Config = oAuth2Config;
        this.tokenRedisCache = tokenRedisCache;
        this.oAuth2ClientMap = oAuth2Clients.stream()
                .collect(Collectors.toMap(OAuth2Client::getName, client -> client));

        this.information.put("providers", getFilteredProviders(oAuth2Config.getProviders()));
        this.information.put("types", oAuth2Config.getOAuth2ClientNames());
    }

    /**
     * Token olish — Redis cache orqali
     */
    public Token getToken(String providerName) {
        ProviderProperties properties = oAuth2Config.getProviderProperties(providerName);
        if (properties == null) {
            throw new RuntimeException("OAuth2 provider not found: " + providerName);
        }

        OAuth2Client client = oAuth2ClientMap.get(properties.getType());
        if (client == null) {
            throw new RuntimeException("OAuth2 client not found for type: " + properties.getType());
        }

        return tokenRedisCache.getToken(providerName, properties, client);
    }

    private Map<String, Object> getFilteredProviders(Map<String, ProviderProperties> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        return providers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Map.of(
                                "type", entry.getValue().getType(),
                                "tokenUrl", entry.getValue().getTokenUrl(),
                                "scope", entry.getValue().getScope())));
    }
}