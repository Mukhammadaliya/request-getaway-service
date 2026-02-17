package uz.greenwhite.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final ConcurrencyProperties concurrencyProperties;

    // ==================== ADMIN CLIENT ====================

    @Bean
    public AdminClient kafkaAdminClient() {
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return AdminClient.create(props);
    }

    // ==================== TOPICS ====================

    @Bean
    public NewTopic requestNewTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().getRequestNew())
                .partitions(concurrencyProperties.getTopicPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic requestResponseTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().getRequestResponse())
                .partitions(concurrencyProperties.getTopicPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic requestDlqTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().getRequestDlq())
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ==================== ERROR HANDLER ====================

    /**
     * Common error handler for all Kafka consumers.
     * - Deserialization errors: logged and skipped (no retry — broken message won't fix itself)
     * - Other errors: retried 2 times with 1 second interval, then skipped
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // FixedBackOff(intervalMs, maxAttempts): retry 2 times with 1s gap, then give up
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    // This is called when all retries are exhausted
                    log.error("Kafka message processing failed permanently — skipping. " +
                                    "topic={}, partition={}, offset={}, key={}, error={}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), exception.getMessage(), exception);
                },
                new FixedBackOff(1000L, 2L)
        );

        // Deserialization errors should NOT be retried — broken JSON won't fix itself
        errorHandler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                org.springframework.kafka.support.serializer.DeserializationException.class
        );

        log.info("Kafka error handler configured: retry=2 attempts, interval=1s, " +
                "deserialization errors=no retry (skip immediately)");

        return errorHandler;
    }

    // ==================== CONSUMER FACTORY ====================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // ErrorHandlingDeserializer wraps the actual deserializer
        // If deserialization fails, it catches the error instead of crashing the consumer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Delegate deserializers (actual ones that do the work)
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JsonDeserializer settings
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "uz.greenwhite.gateway.*");

        // Fetch tuning
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ==================== CONTAINER FACTORIES ====================

    @Bean("requestConsumerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrencyProperties.getMinConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleBetweenPolls(100);
        factory.setCommonErrorHandler(kafkaErrorHandler());

        log.info("Request consumer factory created with initial concurrency: {}",
                concurrencyProperties.getMinConcurrency());

        return factory;
    }

    @Bean("responseConsumerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> responseListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrencyProperties.getMinConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleBetweenPolls(100);
        factory.setCommonErrorHandler(kafkaErrorHandler());

        log.info("Response consumer factory created with initial concurrency: {}",
                concurrencyProperties.getMinConcurrency());

        return factory;
    }

    @Bean("dlqConsumerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleBetweenPolls(500);
        factory.setCommonErrorHandler(kafkaErrorHandler());

        log.info("DLQ consumer factory created with concurrency: 1");

        return factory;
    }
}