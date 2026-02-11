package uz.greenwhite.gateway.notification;

import uz.greenwhite.gateway.model.kafka.DlqMessage;

public interface NotificationService {

    /**
     * DLQ alert yuborish
     */
    void sendDlqAlert(DlqMessage message);

    /**
     * Notification service faolmi?
     */
    boolean isEnabled();
}