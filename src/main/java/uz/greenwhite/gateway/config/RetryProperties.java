package uz.greenwhite.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.retry")
public class RetryProperties {

    /**
     * Maximum retry attempts
     * Default: 3
     */
    private int maxAttempts = 3;

    /**
     * Interval between retries in milliseconds
     * Default: 3000 (3 seconds)
     */
    private long intervalMs = 3000;

    /**
     * Comma-separated HTTP status codes that are retryable
     * Default: 408,429,500,502,503,504
     */
    private String retryableStatuses = "408,429,500,502,503,504";

    /**
     * Cached set of retryable status codes, parsed once at startup
     */
    private Set<Integer> retryableStatusSet;

    @PostConstruct
    public void init() {
        this.retryableStatusSet = Collections.unmodifiableSet(
                Arrays.stream(retryableStatuses.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet())
        );
    }

    /**
     * Check if HTTP status is retryable
     */
    public boolean isRetryable(int httpStatus) {
        return retryableStatusSet.contains(httpStatus);
    }
}