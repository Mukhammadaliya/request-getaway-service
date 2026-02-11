package uz.greenwhite.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.kafka")
public class KafkaProperties {

    /**
     * Kafka bootstrap servers
     * yml: gateway.kafka.bootstrap-servers
     */
    private String bootstrapServers;

    /**
     * Consumer group ID
     * yml: gateway.kafka.group-id
     */
    private String groupId;

    /**
     * Topic nomlari
     * yml: gateway.kafka.topics.*
     */
    private Topics topics = new Topics();

    @PostConstruct
    public void validate() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("gateway.kafka.bootstrap-servers must be configured");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("gateway.kafka.group-id must be configured");
        }
        if (topics.requestNew == null || topics.requestNew.isBlank()) {
            throw new IllegalArgumentException("gateway.kafka.topics.request-new must be configured");
        }
        if (topics.requestResponse == null || topics.requestResponse.isBlank()) {
            throw new IllegalArgumentException("gateway.kafka.topics.request-response must be configured");
        }
        if (topics.requestDlq == null || topics.requestDlq.isBlank()) {
            throw new IllegalArgumentException("gateway.kafka.topics.request-dlq must be configured");
        }

        log.info("Kafka config: servers={}, groupId={}, topics=[{}, {}, {}]",
                bootstrapServers, groupId,
                topics.requestNew, topics.requestResponse, topics.requestDlq);
    }

    @Getter
    @Setter
    public static class Topics {
        private String requestNew;
        private String requestResponse;
        private String requestDlq;
    }
}