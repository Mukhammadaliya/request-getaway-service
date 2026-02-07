package uz.greenwhite.gateway.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.config.RetryProperties;
import uz.greenwhite.gateway.http.HttpRequestService;
import uz.greenwhite.gateway.kafka.producer.RequestProducer;
import uz.greenwhite.gateway.model.enums.ErrorSource;
import uz.greenwhite.gateway.model.enums.RequestStatus;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.state.RequestStateService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestConsumer {

    private final HttpRequestService httpRequestService;
    private final RequestStateService requestStateService;
    private final RequestProducer requestProducer;
    private final RetryProperties retryProperties;

    /**
     * Consume new requests from Kafka and send HTTP requests
     */
    @KafkaListener(
            topics = "${gateway.kafka.topics.request-new}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRequest(ConsumerRecord<String, RequestMessage> record, Acknowledgment ack) {
        String key = record.key();
        RequestMessage message = record.value();

        log.info("Received request: {} [partition={}, offset={}]",
                key, record.partition(), record.offset());

        try {
            // 1. Idempotency check - already completed?
            if (requestStateService.isCompleted(key)) {
                log.warn("Request already completed, skipping: {}", key);
                ack.acknowledge();
                return;
            }

            // 2. Concurrency check - acquire lock
            if (!requestStateService.tryLock(key)) {
                log.warn("Request is being processed by another instance: {}", key);
                ack.acknowledge();
                return;
            }

            try {
                processRequest(key, message);
                ack.acknowledge();

            } finally {
                requestStateService.releaseLock(key);
            }

        } catch (Exception e) {
            log.error("Error processing request {}: {}", key, e.getMessage(), e);
            handleException(key, message, e, ack);
        }
    }

    /**
     * Process single request
     */
    private void processRequest(String key, RequestMessage message) {
        // 1. Create initial state
        requestStateService.createInitialState(key);

        // 2. Update status to SENT
        requestStateService.updateStatus(key, RequestStatus.SENT);

        // 3. Send HTTP request
        ResponseMessage response = httpRequestService.sendRequest(message).block();

        // 4. Handle response
        if (response != null && response.isSuccess()) {
            handleSuccess(key, response);
        } else {
            handleFailedResponse(key, message, response);
        }
    }

    /**
     * Handle successful response
     */
    private void handleSuccess(String key, ResponseMessage response) {
        requestProducer.sendResponse(response);
        requestStateService.updateStatus(key, RequestStatus.COMPLETED);
        log.info("Request processed successfully: {}", key);
    }

    /**
     * Handle failed HTTP response
     */
    private void handleFailedResponse(String key, RequestMessage message, ResponseMessage response) {
        int attemptCount = requestStateService.incrementAttempt(key);
        int httpStatus = response != null ? response.getHttpStatus() : 0;
        String errorMessage = response != null ? response.getErrorMessage() : "Unknown error";

        // Check if retryable and attempts remaining
        boolean isRetryable = retryProperties.isRetryable(httpStatus);
        boolean hasAttemptsLeft = attemptCount < retryProperties.getMaxAttempts();

        if (isRetryable && hasAttemptsLeft) {
            log.warn("Retryable error for {}: status={}, attempt {}/{}",
                    key, httpStatus, attemptCount, retryProperties.getMaxAttempts());
            // Message will be redelivered by Kafka (no ack)
            throw new RuntimeException("Retryable error: " + errorMessage);
        } else {
            // Permanent failure
            handlePermanentFailure(key, message, httpStatus, errorMessage, ErrorSource.HTTP);
        }
    }

    /**
     * Handle exception during processing
     */
    private void handleException(String key, RequestMessage message, Exception e, Acknowledgment ack) {
        int attemptCount = requestStateService.incrementAttempt(key);

        if (attemptCount < retryProperties.getMaxAttempts()) {
            log.warn("Exception for {}, attempt {}/{}, will retry",
                    key, attemptCount, retryProperties.getMaxAttempts());
            // Don't acknowledge - Kafka will redeliver
        } else {
            handlePermanentFailure(key, message, 0, e.getMessage(), ErrorSource.SYSTEM);
            ack.acknowledge(); // Prevent infinite loop
        }
    }

    /**
     * Handle permanent failure - send to DLQ and mark as failed
     */
    private void handlePermanentFailure(String key, RequestMessage message,
                                        int httpStatus, String errorMessage, ErrorSource source) {
        log.error("Request failed permanently: {} - status={}, error={}", key, httpStatus, errorMessage);

        // 1. Mark as failed in Redis
        requestStateService.markFailed(key, errorMessage, source);

        // 2. Send to DLQ for analysis
        requestProducer.sendToDlq(key, message, errorMessage);

        // 3. TODO: Send error response to Oracle (E5 da qilamiz)
    }
}