package uz.greenwhite.gateway.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.model.kafka.DlqMessage;

@Slf4j
@Service
@ConditionalOnMissingBean(TelegramNotificationService.class)
public class NoOpNotificationService implements NotificationService {

    public NoOpNotificationService() {
        log.info("Telegram notification DISABLED — DLQ alerts will only be logged");
    }

    @Override
    public void sendDlqAlert(DlqMessage message) {
        log.warn("DLQ alert (no notification configured): {} - {}",
                message.getCompositeId(), message.getFailureReason());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}