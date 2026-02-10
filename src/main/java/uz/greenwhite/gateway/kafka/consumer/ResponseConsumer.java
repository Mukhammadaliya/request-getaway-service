package uz.greenwhite.gateway.kafka.consumer;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.config.RetryProperties;
import uz.greenwhite.gateway.metrics.GatewayMetrics;
import uz.greenwhite.gateway.model.enums.RequestStatus;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.oracle.BiruniClient;
import uz.greenwhite.gateway.state.RequestStateService;

@Slf4j
@Service
public class ResponseConsumer {

    private final BiruniClient biruniClient;
    private final RequestStateService requestStateService;
    private final RetryProperties retryProperties;
    private final GatewayMetrics metrics;

    public ResponseConsumer(
            BiruniClient biruniClient,
            RequestStateService requestStateService,
            RetryProperties retryProperties,
            GatewayMetrics metrics) {
        this.biruniClient = biruniClient;
        this.requestStateService = requestStateService;
        this.retryProperties = retryProperties;
        this.metrics = metrics;
    }

    @KafkaListener(
            id = "responseConsumer",
            topics = "${gateway.kafka.topics.request-response}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "responseConsumerFactory"
    )
    public void consumeResponse(ConsumerRecord<String, ResponseMessage> record, Acknowledgment ack) {
        String key = record.key();
        ResponseMessage message = record.value();

        log.info("Received response to save: {} [partition={}, offset={}]",
                key, record.partition(), record.offset());

        try {
            // ===== E5: Oracle Save with Timer =====
            Timer.Sample saveSample = Timer.start(metrics.getRegistry());

            boolean saved = saveToOracleWithRetry(message);

            saveSample.stop(metrics.getOracleSaveTimer());

            if (saved) {
                metrics.getOracleSaveSuccess().increment();
                requestStateService.updateStatus(key, RequestStatus.COMPLETED);
                log.info("Response saved to Oracle successfully: {}", key);
            } else {
                metrics.getOracleSaveError().increment();
                saveErrorToOracle(message, "Failed to save response after " +
                        retryProperties.getMaxAttempts() + " attempts");
                requestStateService.updateStatus(key, RequestStatus.FAILED);
                log.error("E5: Failed to save response to Oracle after retries: {}", key);
            }

            ack.acknowledge();

        } catch (Exception e) {
            metrics.getOracleSaveError().increment();
            log.error("E5: Error processing response {}: {}", key, e.getMessage(), e);

            saveErrorToOracle(message, e.getMessage());
            requestStateService.updateStatus(key, RequestStatus.FAILED);

            ack.acknowledge();
        }
    }

    /**
     * Save response to Oracle with retry
     */
    private boolean saveToOracleWithRetry(ResponseMessage message) {
        String key = message.getCompositeId();

        for (int attempt = 1; attempt <= retryProperties.getMaxAttempts(); attempt++) {
            try {
                log.debug("Saving response to Oracle: {} (attempt {}/{})",
                        key, attempt, retryProperties.getMaxAttempts());

                var saveRequest = buildSaveRequest(message);
                boolean saved = biruniClient.saveResponse(saveRequest);

                if (saved) {
                    return true;
                }

                log.warn("E5: Oracle save returned false for {}: attempt {}/{}",
                        key, attempt, retryProperties.getMaxAttempts());

            } catch (Exception e) {
                log.warn("E5: Oracle save failed for {}: attempt {}/{} - {}",
                        key, attempt, retryProperties.getMaxAttempts(), e.getMessage());
            }

            // ===== E5: Retry metric (har bir qayta urinish) =====
            if (attempt < retryProperties.getMaxAttempts()) {
                metrics.getOracleSaveRetry().increment();
                sleepBeforeRetry();
            }
        }

        return false;
    }

    /**
     * Save error response to Oracle
     */
    private void saveErrorToOracle(ResponseMessage message, String errorMessage) {
        String key = message.getCompositeId();

        try {
            log.debug("Saving error response to Oracle: {}", key);

            var errorRequest = BiruniClient.ResponseSaveRequest.builder()
                    .companyId(message.getCompanyId())
                    .requestId(message.getRequestId())
                    .response(null)
                    .errorMessage(errorMessage)
                    .build();

            biruniClient.saveResponse(errorRequest);
            log.info("Error response saved to Oracle: {}", key);

        } catch (Exception e) {
            log.error("E5: Failed to save error response to Oracle: {} - {}", key, e.getMessage());
        }
    }

    private BiruniClient.ResponseSaveRequest buildSaveRequest(ResponseMessage message) {
        BiruniClient.ResponseSaveRequest.ResponseData responseData = null;

        if (message.isSuccess()) {
            responseData = BiruniClient.ResponseSaveRequest.ResponseData.builder()
                    .status(message.getHttpStatus())
                    .contentType(message.getContentType())
                    .body(message.getBody())
                    .build();
        }

        return BiruniClient.ResponseSaveRequest.builder()
                .companyId(message.getCompanyId())
                .requestId(message.getRequestId())
                .response(responseData)
                .errorMessage(message.getErrorMessage())
                .build();
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryProperties.getIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during retry wait");
        }
    }
}