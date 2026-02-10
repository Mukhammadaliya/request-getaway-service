package uz.greenwhite.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Centralized metrics for error tracking and bottleneck detection
 * across all pipeline stages (E1 → E5).
 *
 * Naming convention:
 *   gateway.{stage}.{metric_type}
 *
 * Tags:
 *   stage    = oracle_pull | kafka_produce | consumer_process | http_request | oracle_save
 *   result   = success | error
 *   error    = timeout | connection | auth | serialization | http_4xx | http_5xx | ...
 */
@Slf4j
@Getter
@Component
public class GatewayMetrics {

    private final MeterRegistry registry;

    // ==================== E1: Oracle Pull ====================
    private final Timer oraclePullTimer;
    private final Counter oraclePullSuccess;
    private final Counter oraclePullError;
    private final Counter oraclePullEmpty;

    // ==================== E2: Kafka Produce ====================
    private final Timer kafkaProduceTimer;
    private final Counter kafkaProduceSuccess;
    private final Counter kafkaProduceError;
    private final Counter kafkaProduceRetry;

    // ==================== E3: Consumer Process ====================
    private final Counter consumerReceived;
    private final Counter consumerSkippedDuplicate;
    private final Counter consumerLockFailed;

    // ==================== E4: HTTP Request ====================
    private final Timer httpRequestTimer;
    private final Counter httpSuccess;
    private final Counter httpError4xx;
    private final Counter httpError5xx;
    private final Counter httpTimeout;
    private final Counter httpRetry;
    private final Counter httpCircuitBreakerOpen;

    // ==================== E5: Oracle Save ====================
    private final Timer oracleSaveTimer;
    private final Counter oracleSaveSuccess;
    private final Counter oracleSaveError;
    private final Counter oracleSaveRetry;

    // ==================== DLQ ====================
    private final Counter dlqSent;

    // ==================== End-to-End ====================
    private final Timer requestE2eTimer;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;

        // ==================== E1: Oracle Pull ====================

        this.oraclePullTimer = Timer.builder("gateway.oracle.pull.duration")
                .description("Time taken to pull requests from Oracle")
                .tag("stage", "oracle_pull")
                .register(registry);

        this.oraclePullSuccess = Counter.builder("gateway.oracle.pull.total")
                .description("Total requests pulled from Oracle")
                .tag("stage", "oracle_pull")
                .tag("result", "success")
                .register(registry);

        this.oraclePullError = Counter.builder("gateway.oracle.pull.total")
                .description("Oracle pull errors")
                .tag("stage", "oracle_pull")
                .tag("result", "error")
                .register(registry);

        this.oraclePullEmpty = Counter.builder("gateway.oracle.pull.empty")
                .description("Empty poll cycles (no requests)")
                .tag("stage", "oracle_pull")
                .register(registry);

        // ==================== E2: Kafka Produce ====================

        this.kafkaProduceTimer = Timer.builder("gateway.kafka.produce.duration")
                .description("Time to send request to Kafka")
                .tag("stage", "kafka_produce")
                .register(registry);

        this.kafkaProduceSuccess = Counter.builder("gateway.kafka.produce.total")
                .description("Successful Kafka produces")
                .tag("stage", "kafka_produce")
                .tag("result", "success")
                .register(registry);

        this.kafkaProduceError = Counter.builder("gateway.kafka.produce.total")
                .description("Kafka produce errors")
                .tag("stage", "kafka_produce")
                .tag("result", "error")
                .register(registry);

        this.kafkaProduceRetry = Counter.builder("gateway.kafka.produce.retry")
                .description("Kafka produce retry attempts")
                .tag("stage", "kafka_produce")
                .register(registry);

        // ==================== E3: Consumer Process ====================

        this.consumerReceived = Counter.builder("gateway.consumer.received")
                .description("Total messages received by consumer")
                .tag("stage", "consumer_process")
                .register(registry);

        this.consumerSkippedDuplicate = Counter.builder("gateway.consumer.skipped")
                .description("Messages skipped (already completed)")
                .tag("stage", "consumer_process")
                .tag("reason", "duplicate")
                .register(registry);

        this.consumerLockFailed = Counter.builder("gateway.consumer.skipped")
                .description("Messages skipped (lock failed)")
                .tag("stage", "consumer_process")
                .tag("reason", "lock_failed")
                .register(registry);

        // ==================== E4: HTTP Request ====================

        this.httpRequestTimer = Timer.builder("gateway.http.request.duration")
                .description("HTTP request duration to external API")
                .tag("stage", "http_request")
                .register(registry);

        this.httpSuccess = Counter.builder("gateway.http.request.total")
                .description("Successful HTTP requests")
                .tag("stage", "http_request")
                .tag("result", "success")
                .register(registry);

        this.httpError4xx = Counter.builder("gateway.http.request.total")
                .description("HTTP 4xx client errors")
                .tag("stage", "http_request")
                .tag("result", "error_4xx")
                .register(registry);

        this.httpError5xx = Counter.builder("gateway.http.request.total")
                .description("HTTP 5xx server errors")
                .tag("stage", "http_request")
                .tag("result", "error_5xx")
                .register(registry);

        this.httpTimeout = Counter.builder("gateway.http.request.total")
                .description("HTTP request timeouts")
                .tag("stage", "http_request")
                .tag("result", "timeout")
                .register(registry);

        this.httpRetry = Counter.builder("gateway.http.request.retry")
                .description("HTTP request retry attempts")
                .tag("stage", "http_request")
                .register(registry);

        this.httpCircuitBreakerOpen = Counter.builder("gateway.http.circuitbreaker.rejected")
                .description("Requests rejected by circuit breaker")
                .tag("stage", "http_request")
                .register(registry);

        // ==================== E5: Oracle Save ====================

        this.oracleSaveTimer = Timer.builder("gateway.oracle.save.duration")
                .description("Time to save response to Oracle")
                .tag("stage", "oracle_save")
                .register(registry);

        this.oracleSaveSuccess = Counter.builder("gateway.oracle.save.total")
                .description("Successful Oracle saves")
                .tag("stage", "oracle_save")
                .tag("result", "success")
                .register(registry);

        this.oracleSaveError = Counter.builder("gateway.oracle.save.total")
                .description("Oracle save errors")
                .tag("stage", "oracle_save")
                .tag("result", "error")
                .register(registry);

        this.oracleSaveRetry = Counter.builder("gateway.oracle.save.retry")
                .description("Oracle save retry attempts")
                .tag("stage", "oracle_save")
                .register(registry);

        // ==================== DLQ ====================

        this.dlqSent = Counter.builder("gateway.dlq.sent")
                .description("Messages sent to Dead Letter Queue")
                .register(registry);

        // ==================== End-to-End ====================

        this.requestE2eTimer = Timer.builder("gateway.request.e2e.duration")
                .description("End-to-end request processing time (Oracle Pull → Oracle Save)")
                .register(registry);

        log.info("Gateway pipeline metrics registered (E1-E5 + DLQ + E2E)");
    }

    // ==================== Convenience Methods ====================

    /**
     * Record HTTP result based on status code
     */
    public void recordHttpResult(int statusCode) {
        if (statusCode >= 200 && statusCode < 400) {
            httpSuccess.increment();
        } else if (statusCode >= 400 && statusCode < 500) {
            httpError4xx.increment();
        } else if (statusCode >= 500) {
            httpError5xx.increment();
        }
    }

    /**
     * Record HTTP result as timeout
     */
    public void recordHttpTimeout() {
        httpTimeout.increment();
    }
}