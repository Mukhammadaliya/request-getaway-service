package uz.greenwhite.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.source")
public class GatewayProperties {

    /**
     * Data source base URL
     * Example: http://localhost:8081/b6/b
     */
    private String baseUrl;

    /**
     * Username for Basic Auth
     */
    private String username;

    /**
     * Password for Basic Auth
     */
    private String password;

    /**
     * URI for pulling requests from data source
     * Example: /api/requests/pull
     */
    private String requestPullUri;

    /**
     * URI for saving responses to data source
     * Example: /api/requests/save
     */
    private String responseSaveUri;

    /**
     * Connection timeout in seconds
     */
    private int connectionTimeout = 60;

    /**
     * URI for fetching OAuth2 provider credentials from data source
     */
    private String oauth2ProviderUri;
}