package uz.greenwhite.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.redis")
public class RedisProperties {

    /**
     * Request state TTL in hours.
     * After this period, state will be evicted from Redis.
     * Set higher for systems with long-running requests or weekend gaps.
     * Default: 72 hours (3 days)
     */
    private long stateTtlHours = 72;

    /**
     * Distributed lock TTL in seconds.
     * Lock auto-expires after this time to prevent deadlocks.
     * Default: 300 seconds (5 minutes)
     */
    private long lockTtlSeconds = 300;
}