package uz.greenwhite.gateway.oauth2;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GrantType {
    CLIENT_CREDENTIALS("client_credentials"),
    REFRESH_TOKEN("refresh_token");

    private final String value;

    GrantType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}