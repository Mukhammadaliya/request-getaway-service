package uz.greenwhite.gateway.model.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessage {

    private Long companyId;
    private Long requestId;
    private String originalTopic;
    private String failureReason;
    private String errorSource;      // HTTP, SYSTEM, KAFKA
    private int httpStatus;          // 0 if not HTTP error
    private int attemptCount;
    private String url;              // method + baseUrl + uri
    private String body;             // truncated body (max 500 chars)
    private LocalDateTime failedAt;

    /**
     * Composite ID for Kafka key
     */
    public String getCompositeId() {
        return companyId + ":" + requestId;
    }

    /**
     * Build DLQ message from RequestMessage
     */
    public static DlqMessage from(RequestMessage request, String failureReason,
                                  String errorSource, int httpStatus, int attemptCount) {
        return DlqMessage.builder()
                .companyId(request.getCompanyId())
                .requestId(request.getRequestId())
                .originalTopic("bmb.request.new")
                .failureReason(failureReason)
                .errorSource(errorSource)
                .httpStatus(httpStatus)
                .attemptCount(attemptCount)
                .url(request.getMethod() + " " + request.getBaseUrl() + request.getUri())
                .body(truncate(request.getBody(), 500))
                .failedAt(LocalDateTime.now())
                .build();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...[truncated]";
    }
}