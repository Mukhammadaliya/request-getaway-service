package uz.greenwhite.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.greenwhite.gateway.model.enums.ErrorSource;
import uz.greenwhite.gateway.model.enums.RequestStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestState implements Serializable {

    private String compositeId;
    private RequestStatus status;

    // Retry tracking
    private int attemptCount;
    private String lastError;
    private ErrorSource errorSource;

    // Kafka metadata
    private Long kafkaOffset;
    private Integer kafkaPartition;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}