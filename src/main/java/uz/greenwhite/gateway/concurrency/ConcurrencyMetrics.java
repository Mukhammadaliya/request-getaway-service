package uz.greenwhite.gateway.concurrency;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import uz.greenwhite.gateway.config.KafkaProperties;

import static uz.greenwhite.gateway.concurrency.ConcurrencyMonitorService.REQUEST_LISTENER_ID;
import static uz.greenwhite.gateway.concurrency.ConcurrencyMonitorService.RESPONSE_LISTENER_ID;

@Slf4j
@Component
public class ConcurrencyMetrics {

    private final MeterRegistry meterRegistry;
    private final DynamicConcurrencyManager concurrencyManager;
    private final ConcurrencyMonitorService monitorService;
    private final ThreadPoolTaskExecutor httpExecutor;
    private final KafkaProperties kafkaProperties;

    public ConcurrencyMetrics(
            MeterRegistry meterRegistry,
            DynamicConcurrencyManager concurrencyManager,
            ConcurrencyMonitorService monitorService,
            @Qualifier("httpRequestExecutor") ThreadPoolTaskExecutor httpExecutor,
            KafkaProperties kafkaProperties) {
        this.meterRegistry = meterRegistry;
        this.concurrencyManager = concurrencyManager;
        this.monitorService = monitorService;
        this.httpExecutor = httpExecutor;
        this.kafkaProperties = kafkaProperties;
    }

    @PostConstruct
    public void registerMetrics() {

        // ==================== REQUEST CONSUMER METRICS ====================

        Gauge.builder("gateway.kafka.consumer.concurrency",
                        () -> concurrencyManager.getCurrentConcurrency(REQUEST_LISTENER_ID))
                .description("Current Kafka consumer concurrency level")
                .tag("listener", REQUEST_LISTENER_ID)
                .register(meterRegistry);

        Gauge.builder("gateway.kafka.consumer.lag",
                        () -> monitorService.getLastKnownLag(kafkaProperties.getTopics().getRequestNew()))
                .description("Current Kafka consumer lag")
                .tag("topic", "request-new")
                .tag("listener", REQUEST_LISTENER_ID)
                .register(meterRegistry);

        // ==================== RESPONSE CONSUMER METRICS ====================

        Gauge.builder("gateway.kafka.consumer.concurrency",
                        () -> concurrencyManager.getCurrentConcurrency(RESPONSE_LISTENER_ID))
                .description("Current Kafka consumer concurrency level")
                .tag("listener", RESPONSE_LISTENER_ID)
                .register(meterRegistry);

        Gauge.builder("gateway.kafka.consumer.lag",
                        () -> monitorService.getLastKnownLag(kafkaProperties.getTopics().getRequestResponse()))
                .description("Current Kafka consumer lag")
                .tag("topic", "request-response")
                .tag("listener", RESPONSE_LISTENER_ID)
                .register(meterRegistry);

        // ==================== HTTP THREAD POOL METRICS ====================

        Gauge.builder("gateway.http.pool.active", httpExecutor::getActiveCount)
                .description("Active HTTP request threads")
                .register(meterRegistry);

        Gauge.builder("gateway.http.pool.size", httpExecutor::getPoolSize)
                .description("Current HTTP thread pool size")
                .register(meterRegistry);

        Gauge.builder("gateway.http.pool.queue", () -> httpExecutor.getThreadPoolExecutor().getQueue().size())
                .description("HTTP thread pool queue size")
                .register(meterRegistry);

        log.info("Concurrency metrics registered");
    }
}