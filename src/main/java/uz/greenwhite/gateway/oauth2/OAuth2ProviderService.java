package uz.greenwhite.gateway.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.oauth2.client.OAuth2Client;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;
import uz.greenwhite.gateway.oauth2.model.Token;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OAuth2ProviderService {

    private final OAuth2ProviderFetcher providerFetcher;
    private final OAuth2TokenRedisCache tokenRedisCache;
    private final Map<String, OAuth2Client> oAuth2ClientMap;

    public OAuth2ProviderService(OAuth2ProviderFetcher providerFetcher,
                                 OAuth2TokenRedisCache tokenRedisCache,
                                 List<OAuth2Client> oAuth2Clients) {
        this.providerFetcher = providerFetcher;
        this.tokenRedisCache = tokenRedisCache;
        this.oAuth2ClientMap = oAuth2Clients.stream()
                .collect(Collectors.toMap(OAuth2Client::getName, client -> client));
    }

    /**
     * Acquire OAuth2 token: fetch provider from Oracle (cached) → get token via Redis cache
     */
    public Token getToken(String providerName, Long companyId) {
        ProviderProperties properties = providerFetcher.fetch(providerName, companyId);

        OAuth2Client client = oAuth2ClientMap.get(properties.getType());
        if (client == null) {
            throw new RuntimeException("OAuth2 client not found for type: " + properties.getType());
        }

        return tokenRedisCache.getToken(providerName, properties, client);
    }

    /**
     * Evict provider credentials cache (when credentials are updated in Oracle)
     */
    public void evictProviderCache(String providerName) {
        providerFetcher.evictCache(providerName);
    }
}