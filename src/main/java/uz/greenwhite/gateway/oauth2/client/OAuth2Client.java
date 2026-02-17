package uz.greenwhite.gateway.oauth2.client;

import uz.greenwhite.gateway.oauth2.model.Token;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;

public interface OAuth2Client {

    /**
     * Provider name — must match the "type" in application.yml
     * Example: "biruni", "google", "keycloak"
     */
    String getName();

    /**
     * Get new access token (client_credentials grant)
     */
    Token getAccessToken(ProviderProperties properties);

    /**
     * Refresh existing token (refresh_token grant)
     */
    Token refreshAccessToken(ProviderProperties properties, Token token);
}