package uz.greenwhite.gateway.model.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.greenwhite.gateway.util.StringToMapDeserializer;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestMessage {

    @JsonProperty("company_id")
    private Long companyId;

    @JsonProperty("request_id")
    private Long requestId;

    @JsonProperty("filial_id")
    private Long filialId;

    @JsonProperty("endpoint_id")
    private Long endpointId;

    @JsonProperty("base_url")
    private String baseUrl;

    private String uri;
    private String params;
    private String method;

    @JsonProperty("headers")
    @JsonDeserialize(using = StringToMapDeserializer.class)
    private Map<String, String> headers;

    private String body;

    @JsonProperty("oauth2_provider")
    private String oauth2Provider;

    @JsonProperty("callback_procedure")
    private String callbackProcedure;

    @JsonProperty("project_code")
    private String projectCode;

    @JsonProperty("source_table")
    private String sourceTable;

    @JsonProperty("source_id")
    private Long sourceId;

    private LocalDateTime createdAt;

    public String getCompositeId() {
        return companyId + ":" + requestId;
    }
}