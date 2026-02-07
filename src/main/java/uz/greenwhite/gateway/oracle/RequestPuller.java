package uz.greenwhite.gateway.oracle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.greenwhite.gateway.config.RetryProperties;
import uz.greenwhite.gateway.kafka.producer.RequestProducer;
import uz.greenwhite.gateway.model.kafka.RequestMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.polling.enabled", havingValue = "true")
public class RequestPuller {

    private final BiruniClient biruniClient;
    private final RequestProducer requestProducer;
    private final RetryProperties retryProperties;

    @Scheduled(fixedDelayString = "${gateway.polling.interval-ms:5000}")
    public void pullRequests() {
        try {
            List<RequestMessage> requests;

            do {
                requests = biruniClient.pullRequests();

                if (!requests.isEmpty()) {
                    processRequests(requests);
                }

            } while (!requests.isEmpty());

        } catch (Exception e) {
            log.error("Error while pulling requests from Oracle: {}", e.getMessage(), e);
        }
    }

    /**
     * E1: Process all requests - continue even if some fail
     */
    private void processRequests(List<RequestMessage> requests) {
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();

        for (RequestMessage request : requests) {
            String compositeId = request.getCompositeId();

            try {
                // E2: Send to Kafka with retry
                sendToKafkaWithRetry(request);
                successList.add(compositeId);

            } catch (Exception e) {
                failedList.add(compositeId);
                log.error("Failed to process request: {} - {}", compositeId, e.getMessage());

                // Send to DLQ for analysis
                sendToDlq(request, e.getMessage());
            }
        }

        // Log summary
        logProcessingSummary(requests.size(), successList.size(), failedList);
    }

    /**
     * E2: Send to Kafka with retry (max 3 attempts, 3s interval)
     */
    private void sendToKafkaWithRetry(RequestMessage request) throws Exception {
        String compositeId = request.getCompositeId();
        Exception lastException = null;

        for (int attempt = 1; attempt <= retryProperties.getMaxAttempts(); attempt++) {
            try {
                log.debug("Sending request to Kafka: {} (attempt {}/{})",
                        compositeId, attempt, retryProperties.getMaxAttempts());

                // Send and wait for result
                requestProducer.sendRequest(request).get(10, TimeUnit.SECONDS);

                log.debug("Request sent to Kafka successfully: {}", compositeId);
                return; // Success - exit

            } catch (Exception e) {
                lastException = e;
                log.warn("Kafka send failed for {}: attempt {}/{} - {}",
                        compositeId, attempt, retryProperties.getMaxAttempts(), e.getMessage());

                // Wait before retry (except last attempt)
                if (attempt < retryProperties.getMaxAttempts()) {
                    try {
                        Thread.sleep(retryProperties.getIntervalMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        // All retries failed
        throw new RuntimeException("Failed to send to Kafka after " +
                retryProperties.getMaxAttempts() + " attempts", lastException);
    }

    /**
     * Send failed request to DLQ for analysis
     */
    private void sendToDlq(RequestMessage request, String errorMessage) {
        try {
            requestProducer.sendToDlq(request.getCompositeId(), request, errorMessage);
            log.info("Request sent to DLQ: {}", request.getCompositeId());
        } catch (Exception e) {
            log.error("Failed to send to DLQ: {} - {}", request.getCompositeId(), e.getMessage());
        }
    }

    /**
     * Log processing summary
     */
    private void logProcessingSummary(int total, int success, List<String> failed) {
        if (failed.isEmpty()) {
            log.info("Processed all {} requests successfully", total);
        } else {
            log.warn("Processed {}/{} requests. Failed: {}", success, total, failed);
        }
    }
}