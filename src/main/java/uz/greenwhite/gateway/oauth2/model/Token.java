package uz.greenwhite.gateway.oauth2.model;

import java.io.Serializable;

public record Token(String accessToken,
                    long expiresIn,
                    String tokenType,
                    String refreshToken,
                    long createdAt) implements Serializable {

    private static final long MARGIN_IN_MILLIS = 15 * 1000;

    public Token(String accessToken, long expiresIn, String tokenType, String refreshToken) {
        this(accessToken, expiresIn, tokenType, refreshToken, System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > (createdAt + expiresIn - MARGIN_IN_MILLIS);
    }

    public String getAuthorizationHeader() {
        String header;
        if (tokenType != null && tokenType.equalsIgnoreCase("bearer")) {
            header = "Bearer " + accessToken;
        } else {
            header = tokenType + " " + accessToken;
        }
        return header.trim();
    }
}