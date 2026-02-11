package uz.greenwhite.gateway.oracle;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.greenwhite.gateway.config.RetryProperties;
import uz.greenwhite.gateway.kafka.producer.RequestProducer;
import uz.greenwhite.gateway.metrics.GatewayMetrics;
import uz.greenwhite.gateway.model.kafka.DlqMessage;
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
    private final GatewayMetrics metrics;

    @Scheduled(fixedDelayString = "${gateway.polling.interval-ms:5000}")
    public void pullRequests() {
        try {
            List<RequestMessage> requests;

            do {
                // ===== E1: Oracle Pull with Timer =====
                Timer.Sample pullSample = Timer.start(metrics.getRegistry());

                try {
                    requests = biruniClient.pullRequests();
                } catch (Exception e) {
                    pullSample.stop(metrics.getOraclePullTimer());
                    metrics.getOraclePullError().increment();
                    log.error("E1: Oracle pull failed: {}", e.getMessage(), e);
                    return;
                }

                pullSample.stop(metrics.getOraclePullTimer());

                if (requests.isEmpty()) {
                    metrics.getOraclePullEmpty().increment();
                } else {
                    metrics.getOraclePullSuccess().increment(requests.size());
                    processRequests(requests);
                }

            } while (!requests.isEmpty());

        } catch (Exception e) {
            metrics.getOraclePullError().increment();
            log.error("E1: Error while pulling requests from Oracle: {}", e.getMessage(), e);
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
                // ===== E2: Send to Kafka with retry + Timer =====
                sendToKafkaWithRetry(request);
                successList.add(compositeId);

            } catch (Exception e) {
                failedList.add(compositeId);
                metrics.getKafkaProduceError().increment();
                log.error("E2: Failed to process request: {} - {}", compositeId, e.getMessage());

                // Send to DLQ for analysis
                sendToDlq(request, e.getMessage());
            }
        }

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

                // ===== E2: Kafka Produce with Timer =====
                Timer.Sample kafkaSample = Timer.start(metrics.getRegistry());

                requestProducer.sendRequest(request).get(10, TimeUnit.SECONDS);

                kafkaSample.stop(metrics.getKafkaProduceTimer());
                metrics.getKafkaProduceSuccess().increment();

                log.debug("Request sent to Kafka successfully: {}", compositeId);
                return;

            } catch (Exception e) {
                lastException = e;

                // ===== Retry metric =====
                if (attempt < retryProperties.getMaxAttempts()) {
                    metrics.getKafkaProduceRetry().increment();
                    log.warn("E2: Kafka send failed for {}: attempt {}/{} - {}",
                            compositeId, attempt, retryProperties.getMaxAttempts(), e.getMessage());

                    try {
                        Thread.sleep(retryProperties.getIntervalMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to send to Kafka after " +
                retryProperties.getMaxAttempts() + " attempts", lastException);
    }

    /**
     * Send failed request to DLQ for analysis
     */
    private void sendToDlq(RequestMessage request, String errorMessage) {
        try {
            DlqMessage dlqMessage = DlqMessage.from(request, errorMessage, "KAFKA", 0, retryProperties.getMaxAttempts());
            requestProducer.sendToDlq(dlqMessage);
            metrics.getDlqSent().increment();
            log.info("Request sent to DLQ: {}", request.getCompositeId());
        } catch (Exception e) {
            log.error("Failed to send to DLQ: {} - {}", request.getCompositeId(), e.getMessage());
        }
    }

    private void logProcessingSummary(int total, int success, List<String> failed) {
        if (failed.isEmpty()) {
            log.info("Processed all {} requests successfully", total);
        } else {
            log.warn("Processed {}/{} requests. Failed: {}", success, total, failed);
        }
    }
}