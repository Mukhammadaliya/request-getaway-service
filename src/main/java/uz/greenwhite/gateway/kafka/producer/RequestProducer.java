package uz.greenwhite.gateway.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.model.kafka.DlqMessage;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${gateway.kafka.topics.request-new}")
    private String requestNewTopic;

    @Value("${gateway.kafka.topics.request-response}")
    private String requestResponseTopic;

    @Value("${gateway.kafka.topics.request-dlq}")
    private String requestDlqTopic;

    /**
     * Send new request to Kafka (after pulling from Oracle)
     */
    public CompletableFuture<SendResult<String, Object>> sendRequest(RequestMessage message) {
        String key = message.getCompositeId();
        log.debug("Sending request to Kafka: {}", key);

        return kafkaTemplate.send(requestNewTopic, key, message)
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
        log.debug("Sending response to Kafka: {}", key);

        return kafkaTemplate.send(requestResponseTopic, key, message)
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
    public CompletableFuture<SendResult<String, Object>> sendToDlq(DlqMessage dlqMessage) {
        String key = dlqMessage.getCompositeId();
        log.warn("Sending to DLQ: {} - Reason: {}", key, dlqMessage.getFailureReason());

        return kafkaTemplate.send(requestDlqTopic, key, dlqMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send to DLQ {}: {}", key, ex.getMessage());
                    } else {
                        log.info("Message sent to DLQ: {} [partition={}, offset={}]",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}