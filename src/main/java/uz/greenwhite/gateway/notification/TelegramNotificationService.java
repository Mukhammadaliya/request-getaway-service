package uz.greenwhite.gateway.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uz.greenwhite.gateway.config.TelegramProperties;
import uz.greenwhite.gateway.model.kafka.DlqMessage;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "gateway.telegram.enabled", havingValue = "true")
public class TelegramNotificationService implements NotificationService {

    private final TelegramProperties properties;
    private final RestTemplate restTemplate;
    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/sendMessage";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TelegramNotificationService(TelegramProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        log.info("Telegram notification ENABLED: chatId={}", properties.getChatId());
    }

    @Override
    public void sendDlqAlert(DlqMessage message) {
        try {
            String text = formatMessage(message);
            sendMessage(text);
            log.info("DLQ alert sent to Telegram: {}", message.getCompositeId());
        } catch (Exception e) {
            log.error("Failed to send DLQ alert to Telegram: {} - {}",
                    message.getCompositeId(), e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private String formatMessage(DlqMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔴 <b>DLQ ALERT</b>\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📌 <b>Company:</b> ").append(msg.getCompanyId());
        sb.append(" | <b>Request:</b> ").append(msg.getRequestId()).append("\n");

        if (msg.getUrl() != null) {
            sb.append("🔗 <b>URL:</b> <code>").append(escapeHtml(msg.getUrl())).append("</code>\n");
        }

        sb.append("❌ <b>Sabab:</b> [").append(msg.getErrorSource()).append("] ");
        sb.append(escapeHtml(truncate(msg.getFailureReason(), 200))).append("\n");

        if (msg.getHttpStatus() > 0) {
            sb.append("📡 <b>HTTP Status:</b> ").append(msg.getHttpStatus()).append("\n");
        }

        sb.append("🔄 <b>Urinishlar:</b> ").append(msg.getAttemptCount()).append("\n");

        if (msg.getFailedAt() != null) {
            sb.append("🕐 <b>Vaqt:</b> ").append(msg.getFailedAt().format(FORMATTER)).append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }

    private void sendMessage(String text) {
        String url = String.format(TELEGRAM_API, properties.getBotToken());

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", properties.getChatId());
        body.put("text", text);
        body.put("parse_mode", "HTML");

        if (properties.getMessageThreadId() != null) {
            body.put("message_thread_id", properties.getMessageThreadId());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}