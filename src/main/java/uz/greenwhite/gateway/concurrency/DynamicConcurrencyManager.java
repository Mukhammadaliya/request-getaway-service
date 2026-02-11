package uz.greenwhite.gateway.concurrency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;
import uz.greenwhite.gateway.config.ConcurrencyProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicConcurrencyManager {

    private final KafkaListenerEndpointRegistry registry;
    private final ConcurrencyProperties properties;

    /**
     * Separate state tracking for each listener
     */
    private final Map<String, AtomicInteger> concurrencyMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastScaleTimeMap = new ConcurrentHashMap<>();

    /**
     * Adjust concurrency for a specific listener based on current consumer lag.
     *
     * @param listenerId the @KafkaListener id value
     * @param currentLag current consumer lag (sum across all partitions)
     */
    public void adjustConcurrency(String listenerId, long currentLag) {
        MessageListenerContainer container = registry.getListenerContainer(listenerId);

        if (container == null) {
            log.warn("Listener container not found: {}", listenerId);
            return;
        }

        if (!(container instanceof ConcurrentMessageListenerContainer<?, ?> concurrent)) {
            log.warn("Container is not ConcurrentMessageListenerContainer: {}", listenerId);
            return;
        }

        // Get or create state for this listener
        AtomicInteger currentConcurrency = concurrencyMap.computeIfAbsent(
                listenerId, k -> new AtomicInteger(0));
        AtomicLong lastScaleTime = lastScaleTimeMap.computeIfAbsent(
                listenerId, k -> new AtomicLong(0));

        int actual = concurrent.getConcurrency();
        if (currentConcurrency.compareAndSet(0, actual)) {
            log.info("Initial concurrency detected for [{}]: {}", listenerId, actual);
        }

        int desired = properties.calculateDesiredConcurrency(currentLag);

        // -1 means no change needed (lag is in neutral zone)
        if (desired == -1) {
            log.debug("Lag {} is in neutral zone, no scaling needed for [{}]", currentLag, listenerId);
            return;
        }

        int current = currentConcurrency.get();

        // Already at desired level
        if (desired == current) {
            log.debug("Concurrency already at desired level {} for [{}]", current, listenerId);
            return;
        }

        // Cooldown check — prevent too frequent scaling
        long now = System.currentTimeMillis();
        long lastScale = lastScaleTime.get();
        if (now - lastScale < properties.getScaleCooldownMs()) {
            log.debug("Scaling cooldown active for [{}]. Last scale: {}ms ago",
                    listenerId, now - lastScale);
            return;
        }

        // Step-based scaling (gradual increase/decrease)
        int newConcurrency;
        if (desired > current) {
            newConcurrency = Math.min(current + properties.getScaleStep(), desired);
        } else {
            newConcurrency = Math.max(current - properties.getScaleStep(), desired);
        }

        // Enforce min/max bounds
        newConcurrency = Math.max(properties.getMinConcurrency(), newConcurrency);
        newConcurrency = Math.min(properties.getMaxConcurrency(), newConcurrency);

        if (newConcurrency == current) {
            return;
        }

        // Apply new concurrency level
        try {
            concurrent.setConcurrency(newConcurrency);

            // Instead of stop/start — only applied on next rebalance.
            // This does not halt existing threads, just sets the new target.
            if (newConcurrency > current) {
                // Scale UP — restart needed to spawn new consumer threads
                concurrent.stop();
                concurrent.start();
            }
            // Scale DOWN — no stop/start needed, threads reduce on next rebalance

            currentConcurrency.set(newConcurrency);
            lastScaleTime.set(now);

            String direction = newConcurrency > current ? "⬆ SCALED UP" : "⬇ SCALED DOWN";
            log.info("{} [{}]: {} → {} (lag: {})",
                    direction, listenerId, current, newConcurrency, currentLag);

        } catch (Exception e) {
            log.error("Failed to adjust concurrency for [{}]: {}", listenerId, e.getMessage(), e);
        }
    }

    /**
     * Get the current concurrency level for a specific listener.
     */
    public int getCurrentConcurrency(String listenerId) {
        AtomicInteger val = concurrencyMap.get(listenerId);
        return val != null ? val.get() : 0;
    }

    /**
     * Get the last scaling timestamp for a specific listener.
     */
    public long getLastScaleTime(String listenerId) {
        AtomicLong val = lastScaleTimeMap.get(listenerId);
        return val != null ? val.get() : 0;
    }
}