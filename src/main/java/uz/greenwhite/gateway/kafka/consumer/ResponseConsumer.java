package uz.greenwhite.gateway.kafka.consumer;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.config.RetryProperties;
import uz.greenwhite.gateway.metrics.GatewayMetrics;
import uz.greenwhite.gateway.model.enums.RequestStatus;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.source.ResponseSinkClient;
import uz.greenwhite.gateway.state.RequestStateService;
import uz.greenwhite.gateway.model.ResponseSaveRequest;

@Slf4j
@Service
public class ResponseConsumer {

    private final ResponseSinkClient responseSinkClient;
    private final RequestStateService requestStateService;
    private final RetryProperties retryProperties;
    private final GatewayMetrics metrics;

    public ResponseConsumer(
            ResponseSinkClient responseSinkClient,
            RequestStateService requestStateService,
            RetryProperties retryProperties,
            GatewayMetrics metrics) {
        this.responseSinkClient = responseSinkClient;
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
            // E5: Save to data source with timer
            Timer.Sample saveSample = Timer.start(metrics.getRegistry());

            boolean saved = saveWithRetry(message);

            saveSample.stop(metrics.getOracleSaveTimer());

            if (saved) {
                metrics.getOracleSaveSuccess().increment();
                requestStateService.updateStatus(key, RequestStatus.COMPLETED);
                log.info("Response saved successfully: {}", key);
            } else {
                metrics.getOracleSaveError().increment();
                saveErrorResponse(message, "Failed to save response after " +
                        retryProperties.getMaxAttempts() + " attempts");
                requestStateService.updateStatus(key, RequestStatus.FAILED);
                log.error("E5: Failed to save response after retries: {}", key);
            }

            ack.acknowledge();

        } catch (Exception e) {
            metrics.getOracleSaveError().increment();
            log.error("E5: Error processing response {}: {}", key, e.getMessage(), e);

            saveErrorResponse(message, e.getMessage());
            requestStateService.updateStatus(key, RequestStatus.FAILED);

            ack.acknowledge();
        }
    }

    /**
     * Save response to data source with retry logic
     */
    private boolean saveWithRetry(ResponseMessage message) {
        String key = message.getCompositeId();

        for (int attempt = 1; attempt <= retryProperties.getMaxAttempts(); attempt++) {
            try {
                log.debug("Saving response: {} (attempt {}/{})",
                        key, attempt, retryProperties.getMaxAttempts());

                var saveRequest = buildSaveRequest(message);
                boolean saved = responseSinkClient.saveResponse(saveRequest);

                if (saved) {
                    return true;
                }

                log.warn("E5: Save returned false for {}: attempt {}/{}",
                        key, attempt, retryProperties.getMaxAttempts());

            } catch (Exception e) {
                log.warn("E5: Save failed for {}: attempt {}/{} - {}",
                        key, attempt, retryProperties.getMaxAttempts(), e.getMessage());
            }

            // E5: Retry metric for each retry attempt
            if (attempt < retryProperties.getMaxAttempts()) {
                metrics.getOracleSaveRetry().increment();
                sleepBeforeRetry();
            }
        }

        return false;
    }

    /**
     * Save error response to data source
     */
    private void saveErrorResponse(ResponseMessage message, String errorMessage) {
        String key = message.getCompositeId();

        try {
            log.debug("Saving error response: {}", key);

            var errorRequest = ResponseSaveRequest.builder()
                    .companyId(message.getCompanyId())
                    .requestId(message.getRequestId())
                    .response(null)
                    .errorMessage(errorMessage)
                    .build();

            responseSinkClient.saveResponse(errorRequest);
            log.info("Error response saved: {}", key);

        } catch (Exception e) {
            log.error("E5: Failed to save error response: {} - {}", key, e.getMessage());
        }
    }

    /**
     * Build save request from response message
     */
    private ResponseSaveRequest buildSaveRequest(ResponseMessage message) {
        ResponseSaveRequest.ResponseData responseData;

        if (message.isSuccess()) {
            responseData = ResponseSaveRequest.ResponseData.builder()
                    .status(message.getHttpStatus())
                    .body(message.getBody())
                    .build();
        } else {
            responseData = ResponseSaveRequest.ResponseData.builder()
                    .status(message.getHttpStatus())
                    .body(message.getErrorMessage())
                    .build();
        }

        return ResponseSaveRequest.builder()
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