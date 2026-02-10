package uz.greenwhite.gateway.oauth2;

import lombok.Data;
import lombok.Getter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.greenwhite.gateway.oauth2.client.OAuth2Client;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway.oauth2", ignoreInvalidFields = true)
public class OAuth2Config {

    private Map<String, ProviderProperties> providers;

    @Autowired
    private List<OAuth2Client> oAuth2Clients;

    @Getter
    private List<String> oAuth2ClientNames;

    @Getter
    private Map<String, String> providerTypeMap;

    @PostConstruct
    private void init() {
        oAuth2ClientNames = oAuth2Clients.stream()
                .map(OAuth2Client::getName)
                .toList();

        if (providers == null) {
            log.info("No OAuth2 providers configured");
            return;
        }

        providerTypeMap = providers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getType()));

        // Validate: har bir provider type uchun OAuth2Client mavjudligini tekshirish
        providerTypeMap.forEach((providerName, type) -> {
            if (!oAuth2ClientNames.contains(type)) {
                throw new RuntimeException(
                        String.format("Invalid OAuth2 provider type '%s' for provider '%s'. Available types: %s",
                                type, providerName, oAuth2ClientNames));
            }
        });

        log.info("OAuth2 configured: providers={}, types={}", providers.keySet(), oAuth2ClientNames);
    }

    public ProviderProperties getProviderProperties(String name) {
        if (providers == null) return null;
        return providers.get(name);
    }
}