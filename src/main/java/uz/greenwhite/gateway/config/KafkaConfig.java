package uz.greenwhite.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final ConcurrencyProperties concurrencyProperties;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${gateway.kafka.topics.request-new}")
    private String requestNewTopic;

    @Value("${gateway.kafka.topics.request-response}")
    private String requestResponseTopic;

    @Value("${gateway.kafka.topics.request-dlq}")
    private String requestDlqTopic;

    // ==================== ADMIN CLIENT (lag monitoring uchun) ====================

    @Bean
    public AdminClient kafkaAdminClient() {
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return AdminClient.create(props);
    }

    // ==================== TOPICS ====================

    @Bean
    public NewTopic requestNewTopic() {
        return TopicBuilder.name(requestNewTopic)
                .partitions(concurrencyProperties.getTopicPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic requestResponseTopic() {
        return TopicBuilder.name(requestResponseTopic)
                .partitions(concurrencyProperties.getTopicPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic requestDlqTopic() {
        return TopicBuilder.name(requestDlqTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ==================== CONSUMER FACTORY ====================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "uz.greenwhite.gateway.*");

        // Fetch tuning — katta batch olish uchun
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);           // 1KB minimum fetch
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);          // max 500ms kutish
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);            // bir poll da max 50 record

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ==================== CONTAINER FACTORIES ====================

    /**
     * Request consumer — asosiy processing, dynamic scaling
     * ID: "requestConsumerFactory" — DynamicConcurrencyManager shu nom bilan topadi
     */
    @Bean("requestConsumerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrencyProperties.getMinConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleBetweenPolls(100); // 100ms between polls

        log.info("Request consumer factory created with initial concurrency: {}",
                concurrencyProperties.getMinConcurrency());

        return factory;
    }

    /**
     * Response consumer — Oracle ga save qilish, alohida factory
     */
    @Bean("responseConsumerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> responseListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrencyProperties.getMinConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleBetweenPolls(100);

        log.info("Response consumer factory created with initial concurrency: {}",
                concurrencyProperties.getMinConcurrency());

        return factory;
    }

    /**
     * DLQ consumer — Telegram notification uchun, past concurrency
     */
    @Bean("dlqConsumerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setIdleBetweenPolls(500);

        log.info("DLQ consumer factory created with concurrency: 1");

        return factory;
    }
}