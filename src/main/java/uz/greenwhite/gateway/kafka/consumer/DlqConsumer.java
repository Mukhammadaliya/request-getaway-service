package uz.greenwhite.gateway.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.model.kafka.DlqMessage;
import uz.greenwhite.gateway.notification.NotificationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            id = "dlqConsumer",
            topics = "${gateway.kafka.topics.request-dlq}",
            groupId = "${gateway.kafka.group-id}",
            containerFactory = "dlqConsumerFactory"
    )
    public void consumeDlq(ConsumerRecord<String, DlqMessage> record, Acknowledgment ack) {
        String key = record.key();
        DlqMessage message = record.value();

        log.info("DLQ message received: {} [partition={}, offset={}]",
                key, record.partition(), record.offset());

        try {
            notificationService.sendDlqAlert(message);
        } catch (Exception e) {
            log.error("Failed to process DLQ message {}: {}", key, e.getMessage());
        }

        ack.acknowledge();
    }
}