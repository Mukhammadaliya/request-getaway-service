package uz.greenwhite.gateway.state;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uz.greenwhite.gateway.model.RequestState;
import uz.greenwhite.gateway.model.enums.ErrorSource;
import uz.greenwhite.gateway.model.enums.RequestStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestStateService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATE_PREFIX = "request:state:";
    private static final String LOCK_PREFIX = "request:lock:";
    private static final long STATE_TTL_HOURS = 24;
    private static final long LOCK_TTL_SECONDS = 300;

    // ==================== STATE OPERATIONS ====================

    /**
     * Save request state to Redis
     */
    public void saveState(RequestState state) {
        String key = STATE_PREFIX + state.getCompositeId();
        state.setUpdatedAt(LocalDateTime.now());

        redisTemplate.opsForValue().set(key, state, STATE_TTL_HOURS, TimeUnit.HOURS);
        log.debug("State saved: {} -> {}", key, state.getStatus());
    }

    /**
     * Get request state from Redis
     */
    public Optional<RequestState> getState(String compositeId) {
        String key = STATE_PREFIX + compositeId;
        Object value = redisTemplate.opsForValue().get(key);

        if (value instanceof RequestState state) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    /**
     * Create initial state for new request
     */
    public RequestState createInitialState(String compositeId) {
        RequestState state = RequestState.builder()
                .compositeId(compositeId)
                .status(RequestStatus.PROCESSING)
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        saveState(state);
        return state;
    }

    /**
     * Update status
     */
    public void updateStatus(String compositeId, RequestStatus status) {
        getState(compositeId).ifPresent(state -> {
            state.setStatus(status);
            state.setUpdatedAt(LocalDateTime.now());
            saveState(state);
            log.info("Status updated: {} -> {}", compositeId, status);
        });
    }

    /**
     * Mark as failed
     */
    public void markFailed(String compositeId, String error, ErrorSource source) {
        getState(compositeId).ifPresent(state -> {
            state.markFailed(error, source);
            saveState(state);
            log.warn("Request marked as failed: {} - {}", compositeId, error);
        });
    }

    /**
     * Increment attempt count
     */
    public int incrementAttempt(String compositeId) {
        Optional<RequestState> stateOpt = getState(compositeId);
        if (stateOpt.isPresent()) {
            RequestState state = stateOpt.get();
            state.incrementAttempt();
            saveState(state);
            return state.getAttemptCount();
        }
        return 0;
    }

    /**
     * Check if request is already completed
     */
    public boolean isCompleted(String compositeId) {
        return getState(compositeId)
                .map(state -> state.getStatus() == RequestStatus.DONE ||
                        state.getStatus() == RequestStatus.FAILED)
                .orElse(false);
    }

    /**
     * Delete state
     */
    public void deleteState(String compositeId) {
        String key = STATE_PREFIX + compositeId;
        redisTemplate.delete(key);
        log.debug("State deleted: {}", compositeId);
    }

    // ==================== LOCK OPERATIONS ====================

    /**
     * Try to acquire lock (for idempotency)
     */
    public boolean tryLock(String compositeId) {
        String key = LOCK_PREFIX + compositeId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, LocalDateTime.now().toString(),
                        Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock acquired: {}", compositeId);
            return true;
        }
        log.debug("Lock already exists: {}", compositeId);
        return false;
    }

    /**
     * Release lock
     */
    public void releaseLock(String compositeId) {
        String key = LOCK_PREFIX + compositeId;
        redisTemplate.delete(key);
        log.debug("Lock released: {}", compositeId);
    }

    /**
     * Check if lock exists
     */
    public boolean isLocked(String compositeId) {
        String key = LOCK_PREFIX + compositeId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get current attempt count
     */
    public int getAttemptCount(String compositeId) {
        return getState(compositeId)
                .map(RequestState::getAttemptCount)
                .orElse(0);
    }
}