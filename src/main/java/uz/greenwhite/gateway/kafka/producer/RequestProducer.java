package uz.greenwhite.gateway.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.config.KafkaProperties;
import uz.greenwhite.gateway.model.kafka.DlqMessage;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    /**
     * Send new request to Kafka (after pulling from Oracle)
     */
    public CompletableFuture<SendResult<String, Object>> sendRequest(RequestMessage message) {
        String key = message.getCompositeId();
        String topic = kafkaProperties.getTopics().getRequestNew();
        log.debug("Sending request to Kafka: {}", key);

        return kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send request {}: {}", key, ex.getMessage());
                    } else {
                        log.info("Request sent successfully: {} [partition={}, offset={}]",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Send response to Kafka (after HTTP call)
     */
    public CompletableFuture<SendResult<String, Object>> sendResponse(ResponseMessage message) {
        String key = message.getCompositeId();
        String topic = kafkaProperties.getTopics().getRequestResponse();
        log.debug("Sending response to Kafka: {}", key);

        return kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send response {}: {}", key, ex.getMessage());
                    } else {
                        log.info("Response sent successfully: {} [partition={}, offset={}]",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Send failed message to DLQ
     */
    public CompletableFuture<SendResult<String, Object>> sendToDlq(DlqMessage message) {
        String key = message.getCompositeId();
        String topic = kafkaProperties.getTopics().getRequestDlq();
        log.warn("Sending to DLQ: {} - reason: {}", key, message.getFailureReason());

        return kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send to DLQ {}: {}", key, ex.getMessage());
                    } else {
                        log.info("DLQ message sent: {} [partition={}, offset={}]",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}