package uz.greenwhite.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.http")
public class HttpProperties {

    private int connectTimeoutMs;
    private int readTimeoutMs;
    private int writeTimeoutMs;

    @PostConstruct
    public void validate() {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("gateway.http.connect-timeout-ms must be > 0");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalArgumentException("gateway.http.read-timeout-ms must be > 0");
        }
        if (writeTimeoutMs <= 0) {
            throw new IllegalArgumentException("gateway.http.write-timeout-ms must be > 0");
        }

        log.info("HTTP client config: connect={}ms, read={}ms, write={}ms",
                connectTimeoutMs, readTimeoutMs, writeTimeoutMs);
    }
}