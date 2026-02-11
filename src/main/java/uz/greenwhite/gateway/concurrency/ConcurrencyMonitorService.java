package uz.greenwhite.gateway.concurrency;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.config.ConcurrencyProperties;
import uz.greenwhite.gateway.config.KafkaProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrencyMonitorService {

    private final AdminClient adminClient;
    private final DynamicConcurrencyManager concurrencyManager;
    private final ConcurrencyProperties properties;
    private final KafkaProperties kafkaProperties;

    /**
     * Listener ID lar — @KafkaListener(id = "...") bilan bir xil
     */
    public static final String REQUEST_LISTENER_ID = "requestConsumer";
    public static final String RESPONSE_LISTENER_ID = "responseConsumer";

    private final Map<String, Long> lagMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("ConcurrencyMonitor started: group={}, topics=[{}, {}], interval={}ms",
                kafkaProperties.getGroupId(),
                kafkaProperties.getTopics().getRequestNew(),
                kafkaProperties.getTopics().getRequestResponse(),
                properties.getMonitorIntervalMs());
    }

    /**
     * Har N millisekundda barcha consumer lar uchun lag tekshirish
     */
    @Scheduled(fixedDelayString = "${gateway.concurrency.monitor-interval-ms:10000}")
    public void monitorAndScale() {
        try {
            // 1. Consumer group ning barcha committed offset larini bir marta olish
            Map<TopicPartition, OffsetAndMetadata> allCommittedOffsets = getCommittedOffsets();

            if (allCommittedOffsets == null || allCommittedOffsets.isEmpty()) {
                log.debug("No committed offsets found for group: {}", kafkaProperties.getGroupId());
                return;
            }

            // 2. Request topic lag → RequestConsumer scaling
            String requestTopic = kafkaProperties.getTopics().getRequestNew();
            long requestLag = calculateLagForTopic(requestTopic, allCommittedOffsets);
            lagMap.put(requestTopic, requestLag);
            log.debug("Consumer lag [{}]: {} messages", requestTopic, requestLag);
            concurrencyManager.adjustConcurrency(REQUEST_LISTENER_ID, requestLag);

            // 3. Response topic lag → ResponseConsumer scaling
            String responseTopic = kafkaProperties.getTopics().getRequestResponse();
            long responseLag = calculateLagForTopic(responseTopic, allCommittedOffsets);
            lagMap.put(responseTopic, responseLag);
            log.debug("Consumer lag [{}]: {} messages", responseTopic, responseLag);
            concurrencyManager.adjustConcurrency(RESPONSE_LISTENER_ID, responseLag);

        } catch (Exception e) {
            log.error("Error monitoring consumer lag: {}", e.getMessage(), e);
        }
    }

    /**
     * Consumer group ning committed offset larini olish
     */
    private Map<TopicPartition, OffsetAndMetadata> getCommittedOffsets() throws Exception {
        ListConsumerGroupOffsetsResult result =
                adminClient.listConsumerGroupOffsets(kafkaProperties.getGroupId());
        return result.partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
    }

    /**
     * Bitta topic uchun lag hisoblash
     */
    private long calculateLagForTopic(String topicName,
                                      Map<TopicPartition, OffsetAndMetadata> allCommittedOffsets) throws Exception {

        // 1. Shu topic ning committed offset larini filter qilish
        Map<TopicPartition, OffsetAndMetadata> topicOffsets = allCommittedOffsets.entrySet().stream()
                .filter(e -> e.getKey().topic().equals(topicName))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (topicOffsets.isEmpty()) {
            return 0;
        }

        // 2. End offset larini olish
        Map<TopicPartition, OffsetSpec> offsetSpecMap = topicOffsets.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                adminClient.listOffsets(offsetSpecMap).all().get(10, TimeUnit.SECONDS);

        // 3. Lag hisoblash
        long totalLag = 0;
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : topicOffsets.entrySet()) {
            TopicPartition tp = entry.getKey();
            long committed = entry.getValue().offset();
            long end = endOffsets.containsKey(tp) ? endOffsets.get(tp).offset() : committed;
            totalLag += Math.max(0, end - committed);
        }

        return totalLag;
    }

    /**
     * Tashqi classlar uchun lag olish (metrics, health)
     */
    public long getLastKnownLag(String topicName) {
        return lagMap.getOrDefault(topicName, 0L);
    }
}