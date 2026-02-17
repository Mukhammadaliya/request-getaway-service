package uz.greenwhite.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ThreadPoolConfig {

    private final ConcurrencyProperties concurrencyProperties;

    /**
     * Dedicated thread pool for HTTP requests.
     *
     * Kafka consumer threads only receive messages and
     * delegate HTTP work to this pool.
     * Result: consumer threads stay free to accept new messages.
     */
    @Bean("httpRequestExecutor")
    public ThreadPoolTaskExecutor httpRequestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core — always alive
        executor.setCorePoolSize(concurrencyProperties.getMinConcurrency());

        // Max — scales up under load
        executor.setMaxPoolSize(concurrencyProperties.getMaxConcurrency() * 2);

        // Queue — tasks wait here when max pool is full
        executor.setQueueCapacity(50);

        // Idle threads are terminated after 60s
        executor.setKeepAliveSeconds(60);

        // Thread name prefix — for debugging and monitoring
        executor.setThreadNamePrefix("http-req-");

        // Rejection policy — what to do when queue is also full
        executor.setRejectedExecutionHandler(new CallerRunsWithLogging());

        // Allow core threads to timeout (saves resources)
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("HTTP Request ThreadPool created: core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                200);

        return executor;
    }

    /**
     * Custom rejection handler:
     * When queue is full — caller thread executes the task (backpressure).
     * This slows down the Kafka consumer thread,
     * which naturally slows message consumption — natural backpressure.
     */
    static class CallerRunsWithLogging implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("⚠ HTTP thread pool exhausted! queue={}, active={}, pool={}. " +
                            "Task will run on caller thread (backpressure active)",
                    executor.getQueue().size(),
                    executor.getActiveCount(),
                    executor.getPoolSize());

            // Runs on caller thread — which is the Kafka consumer thread
            // resulting in consumer slowdown (natural backpressure)
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}