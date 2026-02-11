package uz.greenwhite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.greenwhite.gateway.kafka.producer.RequestProducer;
import uz.greenwhite.gateway.model.RequestState;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.DlqMessage;
import uz.greenwhite.gateway.notification.TelegramNotificationService;
import uz.greenwhite.gateway.state.RequestStateService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for manual testing
 * DELETE THIS IN PRODUCTION!
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final RequestProducer requestProducer;
    private final RequestStateService requestStateService;
    private final TelegramNotificationService telegramService;

    /**
     * Send test request to Kafka
     *
     * Example: POST http://localhost:8090/api/test/send
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendTestRequest() {
        // Create test request
        RequestMessage request = RequestMessage.builder()
                .companyId(100L)
                .requestId(System.currentTimeMillis())  // Unique ID
                .filialId(1L)
                .endpointId(1L)
                .baseUrl("https://httpbin.org")  // Free test API
                .uri("/post")
                .method("POST")
                .headers(Map.of("X-Test-Header", "test-value"))
                .body("{\"test\": \"data\", \"timestamp\": \"" + LocalDateTime.now() + "\"}")
                .callbackProcedure("bmb_test.process_response")
                .projectCode("TEST")
                .createdAt(LocalDateTime.now())
                .build();

        // Send to Kafka
        requestProducer.sendRequest(request);

        // Response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "sent");
        response.put("compositeId", request.getCompositeId());
        response.put("message", "Request sent to Kafka topic: bmb.request.new");

        log.info("Test request sent: {}", request.getCompositeId());
        return ResponseEntity.ok(response);
    }

    /**
     * Check request state in Redis
     *
     * Example: GET http://localhost:8090/api/test/state/100:1234567890
     */
    @GetMapping("/state/{compositeId}")
    public ResponseEntity<Object> getState(@PathVariable String compositeId) {
        return requestStateService.getState(compositeId)
                .map(state -> ResponseEntity.ok((Object) state))
                .orElse(ResponseEntity.ok(Map.of(
                        "status", "not_found",
                        "compositeId", compositeId
                )));
    }

    /**
     * Health check
     *
     * Example: GET http://localhost:8090/api/test/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "request-gateway-service",
                "timestamp", LocalDateTime.now().toString()
        ));
    }


    /**
     * Send test request WITH OAuth2 to Kafka
     *
     * Example: POST http://localhost:8090/api/test/send-oauth2
     * Example: POST http://localhost:8090/api/test/send-oauth2?provider=smartup
     */
    @PostMapping("/send-oauth2")
    public ResponseEntity<Map<String, Object>> sendTestOAuth2Request(
            @RequestParam(defaultValue = "smartup") String provider) {

        RequestMessage request = RequestMessage.builder()
                .companyId(100L)
                .requestId(System.currentTimeMillis())
                .filialId(1L)
                .endpointId(1L)
                .baseUrl("https://httpbin.org")
                .uri("/post")
                .method("POST")
                .headers(Map.of("X-Test-Header", "oauth2-test"))
                .body("{\"test\": \"oauth2\", \"provider\": \"" + provider + "\"}")
                .oauth2Provider(provider)
                .projectCode("TEST")
                .createdAt(LocalDateTime.now())
                .build();

        requestProducer.sendRequest(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "sent");
        response.put("compositeId", request.getCompositeId());
        response.put("oauth2Provider", provider);
        response.put("message", "OAuth2 request sent to Kafka");

        log.info("OAuth2 test request sent: {} with provider: {}", request.getCompositeId(), provider);
        return ResponseEntity.ok(response);
    }

    /**
     * Test DLQ → Telegram notification
     *
     * Example: POST http://localhost:8090/api/test/dlq
     */
    @PostMapping("/dlq")
    public ResponseEntity<Map<String, Object>> testDlqNotification() {
        DlqMessage dlqMessage = DlqMessage.builder()
                .companyId(100L)
                .requestId(System.currentTimeMillis())
                .originalTopic("bmb.request.new")
                .failureReason("HTTP 503 Service Unavailable after 3 retries")
                .errorSource("HTTP")
                .httpStatus(503)
                .attemptCount(3)
                .url("POST https://api.example.com/v1/students")
                .body("{\"test\": \"data\"}")
                .failedAt(java.time.LocalDateTime.now())
                .build();

        requestProducer.sendToDlq(dlqMessage);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "sent_to_dlq");
        response.put("compositeId", dlqMessage.getCompositeId());
        response.put("message", "Check Telegram for notification");
        return ResponseEntity.ok(response);
    }
}