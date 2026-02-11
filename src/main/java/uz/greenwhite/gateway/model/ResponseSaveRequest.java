package uz.greenwhite.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseSaveRequest {

    @JsonProperty("company_id")
    private Long companyId;

    @JsonProperty("request_id")
    private Long requestId;

    @JsonProperty("response")
    private ResponseData response;

    @JsonProperty("error_message")
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseData {

        @JsonProperty("status")
        private int status;

        @JsonProperty("content_type")
        private String contentType;

        @JsonProperty("body")
        private Object body;
    }
}