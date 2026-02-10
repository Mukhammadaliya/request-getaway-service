package uz.greenwhite.gateway.oauth2.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uz.greenwhite.gateway.oauth2.GrantType;
import uz.greenwhite.gateway.oauth2.model.OAuth2TokenRequest;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;
import uz.greenwhite.gateway.oauth2.model.Token;

@Slf4j
@Component
@RequiredArgsConstructor
public class BiruniOAuth2Client implements OAuth2Client {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String NAME = "biruni";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Token getAccessToken(ProviderProperties properties) {
        OAuth2TokenRequest body = OAuth2TokenRequest.builder()
                .grantType(GrantType.CLIENT_CREDENTIALS)
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .scope(properties.getScope())
                .build();

        return sendTokenRequest(properties.getTokenUrl(), body);
    }

    @Override
    public Token refreshAccessToken(ProviderProperties properties, Token token) {
        OAuth2TokenRequest body = OAuth2TokenRequest.builder()
                .grantType(GrantType.REFRESH_TOKEN)
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .refreshToken(token.refreshToken())
                .build();

        return sendTokenRequest(properties.getTokenUrl(), body);
    }

    private Token sendTokenRequest(String tokenUrl, OAuth2TokenRequest body) {
        try {
            String responseBody = webClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseToken(responseBody);

        } catch (Exception e) {
            log.error("Failed to get OAuth2 token from {}: {}", tokenUrl, e.getMessage());
            throw new RuntimeException("Failed to get OAuth2 token", e);
        }
    }

    private Token parseToken(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            return new Token(
                    json.get("access_token").asText(),
                    json.get("expires_in").asLong() * 1000,
                    json.get("token_type").asText(),
                    json.get("refresh_token").asText()
            );
        } catch (Exception e) {
            log.error("Failed to parse OAuth2 token response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse OAuth2 token", e);
        }
    }
}