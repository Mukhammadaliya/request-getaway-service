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
     * HTTP request yuborish uchun alohida thread pool.
     *
     * Kafka consumer thread lari faqat message ni oladi va
     * HTTP ishni shu pool ga topshiradi.
     * Natija: consumer thread band bo'lmaydi, yangi message qabul qila oladi.
     */
    @Bean("httpRequestExecutor")
    public ThreadPoolTaskExecutor httpRequestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core — doimo tirik turadi
        executor.setCorePoolSize(concurrencyProperties.getMinConcurrency());

        // Max — yuklanish vaqtida oshadi
        executor.setMaxPoolSize(concurrencyProperties.getMaxConcurrency() * 2);

        // Queue — max pool ham to'lganda, shu queue da kutadi
        executor.setQueueCapacity(50);

        // Idle thread lar 60s dan keyin yo'q qilinadi
        executor.setKeepAliveSeconds(60);

        // Thread nomlari — debug va monitoring uchun
        executor.setThreadNamePrefix("http-req-");

        // Rejection policy — queue ham to'lganda nima qilish
        executor.setRejectedExecutionHandler(new CallerRunsWithLogging());

        // Core thread lar ham timeout bo'lsin (resource tejash)
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
     * Queue to'lganda — caller thread o'zi bajaradi (backpressure).
     * Bu Kafka consumer thread ni sekinlashtiradi,
     * natijada yangi message olish ham sekinlashadi — tabiiy backpressure.
     */
    static class CallerRunsWithLogging implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("⚠ HTTP thread pool exhausted! queue={}, active={}, pool={}. " +
                            "Task will run on caller thread (backpressure active)",
                    executor.getQueue().size(),
                    executor.getActiveCount(),
                    executor.getPoolSize());

            // Caller thread da bajaradi — bu Kafka consumer thread
            // natijada consumer sekinlashadi (natural backpressure)
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}