package uz.greenwhite.gateway.oauth2.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProviderProperties {
    private String type;
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private String scope;
}