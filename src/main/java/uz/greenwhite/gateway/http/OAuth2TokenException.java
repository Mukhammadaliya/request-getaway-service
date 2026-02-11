package uz.greenwhite.gateway.http;

public class OAuth2TokenException extends RuntimeException {

    public OAuth2TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}