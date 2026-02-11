package uz.greenwhite.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gateway.telegram")
public class TelegramProperties {

    private boolean enabled = true;
    private String botToken;
    private Long chatId;
    private Integer messageThreadId;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;
}