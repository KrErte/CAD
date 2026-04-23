package ee.krerte.cad.pricing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Tracks usage per user (monthly) and per IP (daily, for demo mode).
 * Uses Redis when available, falls back to in-memory ConcurrentHashMap.
 */
@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);

    private final Map<PricingPlan, PlanLimits> limitsMap;
    @Nullable private final StringRedisTemplate redis;

    /** Fallback when Redis is unavailable. */
    private final ConcurrentHashMap<String, AtomicInteger> memoryCounters = new ConcurrentHashMap<>();

    public UsageTrackingService(
            Map<PricingPlan, PlanLimits> limitsMap,
            @Nullable StringRedisTemplate redis) {
        this.limitsMap = limitsMap;
        this.redis = redis;
    }

    public enum UsageKind {
        GENERATION,
        REVIEW,
        MESHY
    }

    public record CheckResult(boolean allowed, int current, int limit) {}

    /**
     * Check and atomically record one usage unit for the given user/plan/kind.
     * Returns whether the action is allowed and the current/limit counts.
     */
    public CheckResult checkAndRecord(long userId, PricingPlan plan, UsageKind kind) {
        PlanLimits limits = limitsMap.getOrDefault(plan, limitsMap.get(PricingPlan.DEMO));
        int limit = limitFor(limits, kind);
        if (limit == -1) {
            return new CheckResult(true, 0, -1); // unlimited
        }
        if (limit == 0) {
            return new CheckResult(false, 0, 0); // not available on this plan
        }

        String key = "usage:" + userId + ":" + kind.name() + ":" + YearMonth.now();
        int current = increment(key);
        return new CheckResult(current <= limit, current, limit);
    }

    /**
     * Check and record a demo usage by IP address (daily limit).
     */
    public CheckResult checkDemo(String ipAddress) {
        PlanLimits limits = limitsMap.get(PricingPlan.DEMO);
        int dailyLimit = limits.dailyLimit();

        String ipHash = sha256(ipAddress);
        String key = "demo:" + ipHash + ":" + LocalDate.now();
        int current = increment(key);
        return new CheckResult(current <= dailyLimit, current, dailyLimit);
    }

    private int limitFor(PlanLimits limits, UsageKind kind) {
        return switch (kind) {
            case GENERATION -> limits.generationsPerMonth();
            case REVIEW -> limits.reviewsPerMonth();
            case MESHY -> limits.meshyPerMonth();
        };
    }

    private int increment(String key) {
        if (redis != null) {
            try {
                Long val = redis.opsForValue().increment(key);
                return val != null ? val.intValue() : 1;
            } catch (Exception e) {
                log.warn("Redis increment failed for {}, falling back to memory: {}", key, e.getMessage());
            }
        }
        return memoryCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
