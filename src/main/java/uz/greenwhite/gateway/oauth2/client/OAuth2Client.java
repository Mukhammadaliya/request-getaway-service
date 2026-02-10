package uz.greenwhite.gateway.oauth2.client;

import uz.greenwhite.gateway.oauth2.model.Token;
import uz.greenwhite.gateway.oauth2.model.ProviderProperties;

public interface OAuth2Client {

    /**
     * Provider nomi — application.yml dagi "type" bilan mos kelishi kerak
     * Masalan: "biruni", "google", "keycloak"
     */
    String getName();

    /**
     * Yangi access token olish (client_credentials grant)
     */
    Token getAccessToken(ProviderProperties properties);

    /**
     * Mavjud token'ni refresh qilish (refresh_token grant)
     */
    Token refreshAccessToken(ProviderProperties properties, Token token);
}