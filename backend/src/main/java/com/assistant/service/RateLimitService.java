package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PHASE 7.1 — Stabilized per-user request rate limiting.
 *
 * Fixes from Phase 7:
 *   - forceRelease() for clean stream cancellation without failure penalty
 *   - Stale lock detection: active generation lock auto-expires after 3 minutes
 *   - Stream failures (IOException = client disconnect) do NOT increment failure counter
 *   - Scheduled cleanup of stale user states
 *   - Remaining requests returned in result for frontend display
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    private boolean isDevMode() {
        return activeProfile != null && (activeProfile.contains("dev") || activeProfile.contains("local") || activeProfile.contains("test"));
    }

    private static final int    MAX_REQUESTS_PER_MINUTE  = 10;
    private static final long   WINDOW_MS                = 60_000L;
    private static final int    FAILURE_COOLDOWN_AFTER   = 3;
    private static final long   FAILURE_COOLDOWN_MS      = 30_000L;
    // Safety valve: if a generation lock is held for > 3 minutes, auto-release it
    private static final long   MAX_LOCK_DURATION_MS     = 180_000L;

    private final Map<Long, UserRateState> userStates = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public RateLimitResult checkAndRecord(Long userId) {
        // Dev mode bypass — never block in development
        if (isDevMode()) {
            return RateLimitResult.allowed(MAX_REQUESTS_PER_MINUTE);
        }

        UserRateState state = userStates.computeIfAbsent(userId, k -> new UserRateState());

        synchronized (state) {
            long now = Instant.now().toEpochMilli();

            // Auto-release stale lock (prevents permanent lock after crash/disconnect)
            if (state.activeGeneration && (now - state.generationStartedAt) > MAX_LOCK_DURATION_MS) {
                log.warn("[rate-limit] Auto-releasing stale lock for user={} (held {}ms)",
                        userId, now - state.generationStartedAt);
                state.activeGeneration = false;
            }

            // Check failure cooldown
            if (state.cooldownUntil > now) {
                long remaining = (state.cooldownUntil - now) / 1000;
                log.warn("[rate-limit] User {} in cooldown for {}s more", userId, remaining);
                return RateLimitResult.cooldown(remaining, 0);
            }

            // Check concurrent generation
            if (state.activeGeneration) {
                log.warn("[rate-limit] User {} already has an active generation", userId);
                return RateLimitResult.concurrent();
            }

            // Slide the window
            if (now - state.windowStart > WINDOW_MS) {
                state.windowStart = now;
                state.requestCount.set(0);
            }

            // Check rate limit
            int count = state.requestCount.incrementAndGet();
            if (count > MAX_REQUESTS_PER_MINUTE) {
                long resetIn = (state.windowStart + WINDOW_MS - now) / 1000;
                int remaining = 0;
                log.warn("[rate-limit] User {} exceeded rate limit ({}/min), resets in {}s", userId, count, resetIn);
                return RateLimitResult.rateLimited(resetIn, remaining);
            }

            // Mark active
            state.activeGeneration    = true;
            state.generationStartedAt = now;
            int remaining = MAX_REQUESTS_PER_MINUTE - count;
            log.debug("[rate-limit] User {} request {}/{} allowed, {} remaining",
                    userId, count, MAX_REQUESTS_PER_MINUTE, remaining);
            return RateLimitResult.allowed(remaining);
        }
    }

    /**
     * Called on successful generation completion.
     * Resets failure counter and releases the active lock.
     */
    public void recordSuccess(Long userId) {
        UserRateState state = userStates.get(userId);
        if (state != null) {
            synchronized (state) {
                state.activeGeneration    = false;
                state.generationStartedAt = 0L;
                state.consecutiveFailures = 0;
                log.debug("[rate-limit] Success recorded for user={}", userId);
            }
        }
    }

    /**
     * Called on genuine backend failure (not client disconnect, not timeout).
     * Increments failure counter and may trigger cooldown.
     */
    public void recordFailure(Long userId) {
        UserRateState state = userStates.get(userId);
        if (state != null) {
            synchronized (state) {
                state.activeGeneration    = false;
                state.generationStartedAt = 0L;
                state.consecutiveFailures++;
                if (state.consecutiveFailures >= FAILURE_COOLDOWN_AFTER) {
                    state.cooldownUntil = Instant.now().toEpochMilli() + FAILURE_COOLDOWN_MS;
                    log.warn("[rate-limit] User {} entered cooldown after {} failures", userId, state.consecutiveFailures);
                }
            }
        }
    }

    /**
     * Called when a stream is cancelled by the client (browser refresh, abort).
     * Releases the active lock WITHOUT incrementing the failure counter.
     * This prevents false 429s after client-side cancellations.
     */
    public void forceRelease(Long userId) {
        UserRateState state = userStates.get(userId);
        if (state != null) {
            synchronized (state) {
                if (state.activeGeneration) {
                    state.activeGeneration    = false;
                    state.generationStartedAt = 0L;
                    log.info("[rate-limit] Force-released lock for user={} (client disconnect/cancel)", userId);
                }
            }
        }
    }

    /**
     * Returns remaining requests in the current window for a user.
     */
    public int getRemainingRequests(Long userId) {
        UserRateState state = userStates.get(userId);
        if (state == null) return MAX_REQUESTS_PER_MINUTE;
        synchronized (state) {
            long now = Instant.now().toEpochMilli();
            if (now - state.windowStart > WINDOW_MS) return MAX_REQUESTS_PER_MINUTE;
            return Math.max(0, MAX_REQUESTS_PER_MINUTE - state.requestCount.get());
        }
    }

    /**
     * Scheduled cleanup: remove stale user states every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanupStaleStates() {
        if (isDevMode()) return; // no cleanup needed in dev
        long now = Instant.now().toEpochMilli();
        int removed = 0;
        for (Map.Entry<Long, UserRateState> entry : userStates.entrySet()) {
            UserRateState state = entry.getValue();
            synchronized (state) {
                // Remove if no activity for 10 minutes and no active generation
                boolean stale = !state.activeGeneration
                        && (now - state.windowStart) > 600_000L
                        && state.cooldownUntil < now;
                if (stale) {
                    userStates.remove(entry.getKey());
                    removed++;
                }
            }
        }
        if (removed > 0) log.debug("[rate-limit] Cleaned up {} stale user states", removed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────

    private static class UserRateState {
        volatile long         windowStart          = Instant.now().toEpochMilli();
        final    AtomicInteger requestCount         = new AtomicInteger(0);
        volatile boolean      activeGeneration      = false;
        volatile long         generationStartedAt   = 0L;
        volatile int          consecutiveFailures   = 0;
        volatile long         cooldownUntil         = 0L;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result types
    // ─────────────────────────────────────────────────────────────────────────

    public enum LimitType { ALLOWED, RATE_LIMITED, CONCURRENT, COOLDOWN }

    public static class RateLimitResult {
        public final LimitType type;
        public final boolean   allowed;
        public final long      retryAfterSeconds;
        public final int       remainingRequests;
        public final String    message;

        private RateLimitResult(LimitType type, boolean allowed, long retryAfterSeconds,
                                int remainingRequests, String message) {
            this.type              = type;
            this.allowed           = allowed;
            this.retryAfterSeconds = retryAfterSeconds;
            this.remainingRequests = remainingRequests;
            this.message           = message;
        }

        public static RateLimitResult allowed(int remaining) {
            return new RateLimitResult(LimitType.ALLOWED, true, 0, remaining, null);
        }
        public static RateLimitResult rateLimited(long retryAfter, int remaining) {
            return new RateLimitResult(LimitType.RATE_LIMITED, false, retryAfter, remaining,
                    "Rate limit exceeded. Try again in " + retryAfter + " seconds.");
        }
        public static RateLimitResult concurrent() {
            return new RateLimitResult(LimitType.CONCURRENT, false, 3, 0,
                    "A generation is already in progress. Please wait.");
        }
        public static RateLimitResult cooldown(long retryAfter, int remaining) {
            return new RateLimitResult(LimitType.COOLDOWN, false, retryAfter, remaining,
                    "Too many failures. Cooling down for " + retryAfter + " more seconds.");
        }
    }
}
