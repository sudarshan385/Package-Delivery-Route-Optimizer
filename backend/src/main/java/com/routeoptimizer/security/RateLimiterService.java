package com.routeoptimizer.security;

import com.routeoptimizer.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Concurrency-safe in-memory rate limiter providing IP-based and identifier-based throttling.
 *
 * Hardened against D1 (Rate-Limit Map Reset Attack):
 * 1. Selective expired entry eviction — NEVER executes blanket map.clear().
 * 2. Strict capacity bound (maxTrackedEntries) with fail-closed rejection for new admissions when full.
 * 3. Preserves all active IP counters and identifier cooldowns even under sustained key-flooding attacks.
 * 4. Deterministic time abstraction (timeSupplier) enabling millisecond-accurate testing without sleeps.
 * 5. Safe generic 429 response on capacity rejection preventing account-existence leaks.
 */
@Service
public class RateLimiterService {

    public static final int DEFAULT_MAX_TRACKED_ENTRIES = 10000;

    private final int maxTrackedEntries;
    private final int loginIpMaxAttempts;
    private final long loginIpWindowMillis;

    private final int loginIdentifierMaxFailures;
    private final long loginIdentifierCooldownMillis;

    private final int registerIpMaxAttempts;
    private final long registerIpWindowMillis;

    private final Supplier<Long> timeSupplier;

    private final ConcurrentHashMap<String, SlidingWindowCounter> loginIpCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FailedAttemptTracker> loginIdentifierTrackers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingWindowCounter> registerIpCounters = new ConcurrentHashMap<>();

    public RateLimiterService() {
        this(10, 60, 5, 300, 5, 300);
    }

    @Autowired
    public RateLimiterService(
            @Value("${app.security.rate-limit.login.ip-max-attempts:10}") int loginIpMaxAttempts,
            @Value("${app.security.rate-limit.login.ip-window-seconds:60}") long loginIpWindowSeconds,
            @Value("${app.security.rate-limit.login.identifier-max-failures:5}") int loginIdentifierMaxFailures,
            @Value("${app.security.rate-limit.login.identifier-cooldown-seconds:300}") long loginIdentifierCooldownSeconds,
            @Value("${app.security.rate-limit.register.ip-max-attempts:5}") int registerIpMaxAttempts,
            @Value("${app.security.rate-limit.register.ip-window-seconds:300}") long registerIpWindowSeconds) {
        this(DEFAULT_MAX_TRACKED_ENTRIES, loginIpMaxAttempts, loginIpWindowSeconds,
                loginIdentifierMaxFailures, loginIdentifierCooldownSeconds,
                registerIpMaxAttempts, registerIpWindowSeconds, System::currentTimeMillis);
    }

    public RateLimiterService(
            int maxTrackedEntries,
            int loginIpMaxAttempts,
            long loginIpWindowSeconds,
            int loginIdentifierMaxFailures,
            long loginIdentifierCooldownSeconds,
            int registerIpMaxAttempts,
            long registerIpWindowSeconds,
            Supplier<Long> timeSupplier) {
        this.maxTrackedEntries = Math.max(1, maxTrackedEntries);
        this.loginIpMaxAttempts = loginIpMaxAttempts;
        this.loginIpWindowMillis = loginIpWindowSeconds * 1000L;
        this.loginIdentifierMaxFailures = loginIdentifierMaxFailures;
        this.loginIdentifierCooldownMillis = loginIdentifierCooldownSeconds * 1000L;
        this.registerIpMaxAttempts = registerIpMaxAttempts;
        this.registerIpWindowMillis = registerIpWindowSeconds * 1000L;
        this.timeSupplier = timeSupplier != null ? timeSupplier : System::currentTimeMillis;
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        // Strict safe IP extraction: Do not trust unverified proxy headers
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null && !remoteAddr.trim().isEmpty() ? remoteAddr.trim() : "unknown";
    }

    public void checkLoginIpRate(String clientIp) {
        checkRate(loginIpCounters, clientIp, loginIpMaxAttempts, loginIpWindowMillis,
                "Too many login attempts from this IP address. Please retry in %d seconds.");
    }

    public void checkLoginIdentifierRate(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            return;
        }
        String normalized = rawIdentifier.trim().toLowerCase();
        long now = timeSupplier.get();

        FailedAttemptTracker tracker = loginIdentifierTrackers.get(normalized);
        if (tracker != null) {
            long remainingCooldown = tracker.getRemainingCooldownMillis(now);
            if (remainingCooldown > 0) {
                long retryAfterSec = Math.max(1, (remainingCooldown + 999) / 1000);
                throw new RateLimitExceededException(
                        String.format("Too many failed login attempts. Please retry in %d seconds.", retryAfterSec),
                        retryAfterSec
                );
            }
        }
    }

    public void recordLoginSuccess(String rawIdentifier) {
        if (rawIdentifier != null && !rawIdentifier.trim().isEmpty()) {
            loginIdentifierTrackers.remove(rawIdentifier.trim().toLowerCase());
        }
    }

    public void recordLoginFailure(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            return;
        }
        String normalized = rawIdentifier.trim().toLowerCase();
        long now = timeSupplier.get();

        // If key is not yet present and map is full, prune expired entries
        if (!loginIdentifierTrackers.containsKey(normalized)) {
            if (loginIdentifierTrackers.size() >= maxTrackedEntries) {
                pruneExpiredIdentifierTrackers(now);
            }
            // If still full, fail closed: do not admit new keys, but never clear existing active cooldowns!
            if (loginIdentifierTrackers.size() >= maxTrackedEntries) {
                return;
            }
        }

        loginIdentifierTrackers.compute(normalized, (key, existing) -> {
            if (existing == null) {
                return new FailedAttemptTracker(1, now, 0);
            }
            if (existing.cooldownUntilMillis > 0 && now >= existing.cooldownUntilMillis) {
                return new FailedAttemptTracker(1, now, 0);
            }
            int newCount = existing.failureCount + 1;
            long cooldownUntil = 0;
            if (newCount >= loginIdentifierMaxFailures) {
                cooldownUntil = now + loginIdentifierCooldownMillis;
            }
            return new FailedAttemptTracker(newCount, now, cooldownUntil);
        });
    }

    public void checkRegisterIpRate(String clientIp) {
        checkRate(registerIpCounters, clientIp, registerIpMaxAttempts, registerIpWindowMillis,
                "Too many registration attempts from this IP address. Please retry in %d seconds.");
    }

    public void resetAll() {
        loginIpCounters.clear();
        loginIdentifierTrackers.clear();
        registerIpCounters.clear();
    }

    public int getLoginIpCountersSize() {
        return loginIpCounters.size();
    }

    public int getLoginIdentifierTrackersSize() {
        return loginIdentifierTrackers.size();
    }

    public int getRegisterIpCountersSize() {
        return registerIpCounters.size();
    }

    private void checkRate(ConcurrentHashMap<String, SlidingWindowCounter> map, String key,
                           int maxAttempts, long windowMillis, String messageFormat) {
        long now = timeSupplier.get();

        // If key is not yet tracked and map is at or above capacity, prune expired entries selectively
        if (!map.containsKey(key)) {
            if (map.size() >= maxTrackedEntries) {
                pruneExpiredSlidingWindow(map, now, windowMillis);
            }
            // If map remains at capacity after pruning expired entries, reject new untracked keys safely
            // rather than wiping out active counters and resetting protection!
            if (map.size() >= maxTrackedEntries) {
                throw new RateLimitExceededException(
                        "Service capacity reached. Please retry in 60 seconds.",
                        60
                );
            }
        }

        SlidingWindowCounter counter = map.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMillis >= windowMillis) {
                return new SlidingWindowCounter(1, now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > maxAttempts) {
            long elapsed = now - counter.windowStartMillis;
            long remainingMillis = Math.max(0, windowMillis - elapsed);
            long retryAfterSec = Math.max(1, (remainingMillis + 999) / 1000);
            throw new RateLimitExceededException(
                    String.format(messageFormat, retryAfterSec),
                    retryAfterSec
            );
        }
    }

    private void pruneExpiredSlidingWindow(ConcurrentHashMap<String, SlidingWindowCounter> map, long now, long windowMillis) {
        map.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis >= windowMillis);
    }

    private void pruneExpiredIdentifierTrackers(long now) {
        loginIdentifierTrackers.entrySet().removeIf(entry -> {
            FailedAttemptTracker tracker = entry.getValue();
            if (tracker.cooldownUntilMillis > 0 && now >= tracker.cooldownUntilMillis) {
                return true;
            }
            if (tracker.cooldownUntilMillis == 0 && now - tracker.lastAttemptMillis >= loginIdentifierCooldownMillis) {
                return true;
            }
            return false;
        });
    }

    public static class SlidingWindowCounter {
        final AtomicInteger count;
        final long windowStartMillis;

        SlidingWindowCounter(int initialCount, long windowStartMillis) {
            this.count = new AtomicInteger(initialCount);
            this.windowStartMillis = windowStartMillis;
        }

        public int getCount() {
            return count.get();
        }
    }

    public static class FailedAttemptTracker {
        final int failureCount;
        final long lastAttemptMillis;
        final long cooldownUntilMillis;

        FailedAttemptTracker(int failureCount, long lastAttemptMillis, long cooldownUntilMillis) {
            this.failureCount = failureCount;
            this.lastAttemptMillis = lastAttemptMillis;
            this.cooldownUntilMillis = cooldownUntilMillis;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public long getCooldownUntilMillis() {
            return cooldownUntilMillis;
        }

        long getRemainingCooldownMillis(long now) {
            if (cooldownUntilMillis > 0 && now < cooldownUntilMillis) {
                return cooldownUntilMillis - now;
            }
            return 0;
        }
    }
}
