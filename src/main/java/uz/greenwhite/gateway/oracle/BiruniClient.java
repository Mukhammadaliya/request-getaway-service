package uz.greenwhite.gateway.oracle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.greenwhite.gateway.config.GatewayProperties;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.util.AuthUtil;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class BiruniClient {

    private final RestClient restClient;
    private final GatewayProperties properties;

    public BiruniClient(GatewayProperties properties) {
        this.properties = properties;

        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(properties.getConnectionTimeout()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .messageConverters(converters -> {
                    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
                    converter.setSupportedMediaTypes(Arrays.asList(
                            MediaType.APPLICATION_JSON,
                            MediaType.TEXT_PLAIN
                    ));
                    converters.add(converter);
                })
                .build();
    }

    /**
     * Pull new requests from Oracle (status = 'N')
     */
    public List<RequestMessage> pullRequests() {
        try {
            log.debug("Pulling requests from: {}{}", properties.getBaseUrl(), properties.getRequestPullUri());

            List<RequestMessage> requests = restClient.get()
                    .uri(properties.getRequestPullUri())
                    .header(HttpHeaders.AUTHORIZATION,
                            AuthUtil.generateBasicAuth(properties.getUsername(), properties.getPassword()))
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<RequestMessage>>() {})
                    .getBody();

            if (requests != null && !requests.isEmpty()) {
                log.info("Pulled {} requests from Oracle", requests.size());
            }

            return requests != null ? requests : List.of();

        } catch (Exception e) {
            log.error("Error pulling requests from Oracle: {}", e.getMessage(), e);
            throw new RuntimeException("Oracle pull failed: " + e.getMessage(), e);
        }
    }

    /**
     * Save response back to Oracle
     */
    public boolean saveResponse(ResponseSaveRequest request) {
        try {
            log.debug("Saving response for: {}:{}", request.getCompanyId(), request.getRequestId());

            var response = restClient.post()
                    .uri(properties.getResponseSaveUri())
                    .header(HttpHeaders.AUTHORIZATION,
                            AuthUtil.generateBasicAuth(properties.getUsername(), properties.getPassword()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(request))
                    .exchange((req, resp) -> {
                        int status = resp.getStatusCode().value();
                        log.debug("Save response status: {}", status);
                        return status >= 200 && status < 300;
                    });

            if (response) {
                log.info("Response saved successfully: {}:{}", request.getCompanyId(), request.getRequestId());
            }

            return response;

        } catch (Exception e) {
            log.error("Error saving response to Oracle: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Request for saving response to Oracle
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ResponseSaveRequest {
        
        @com.fasterxml.jackson.annotation.JsonProperty("company_id")
        private Long companyId;
        
        @com.fasterxml.jackson.annotation.JsonProperty("request_id")
        private Long requestId;
        
        @com.fasterxml.jackson.annotation.JsonProperty("response")
        private ResponseData response;
        
        @com.fasterxml.jackson.annotation.JsonProperty("error_message")
        private String errorMessage;

        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class ResponseData {
            
            @com.fasterxml.jackson.annotation.JsonProperty("status")
            private int status;
            
            @com.fasterxml.jackson.annotation.JsonProperty("content_type")
            private String contentType;
            
            @com.fasterxml.jackson.annotation.JsonProperty("body")
            private Object body;
        }
    }
}