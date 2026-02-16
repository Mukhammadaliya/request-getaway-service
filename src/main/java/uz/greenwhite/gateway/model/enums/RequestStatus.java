package uz.greenwhite.gateway.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequestStatus {

    NEW("N", "New - Request created"),
    PROCESSING("P", "Processing - Pulled from Oracle"),
    SENT("S", "Sent - HTTP request sent"),
    COMPLETED("C", "Completed - Response received"),
    FAILED("F", "Failed - Error occurred");

    private final String code;
    private final String description;

    public static RequestStatus fromCode(String code) {
        for (RequestStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}