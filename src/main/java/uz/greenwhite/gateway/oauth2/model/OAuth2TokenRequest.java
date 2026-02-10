package uz.greenwhite.gateway.oauth2.model;

import lombok.Data;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uz.greenwhite.gateway.oauth2.GrantType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Data
public class OAuth2TokenRequest {
    @JsonProperty("grant_type")
    private GrantType grantType;
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("client_secret")
    private String clientSecret;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("scope")
    private String scope;
}