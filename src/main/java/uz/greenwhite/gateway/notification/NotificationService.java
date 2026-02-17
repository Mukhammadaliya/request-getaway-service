package uz.greenwhite.gateway.notification;

import uz.greenwhite.gateway.model.kafka.DlqMessage;

public interface NotificationService {

    /**
     * Send DLQ alert notification
     */
    void sendDlqAlert(DlqMessage message);

    /**
     * Is notification service active?
     */
    boolean isEnabled();
}