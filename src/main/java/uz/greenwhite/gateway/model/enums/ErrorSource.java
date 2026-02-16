package uz.greenwhite.gateway.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorSource {

    HTTP("HTTP", "External API error"),
    SYSTEM("SYSTEM", "Internal system error");

    private final String code;
    private final String description;
}