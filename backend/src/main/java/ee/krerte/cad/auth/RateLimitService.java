package ee.krerte.cad.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-user per-bucket sliding-window rate limiter. In-memory, no Redis. Good enough for
 * single-instance deploy; swap to Redis when scaling horizontally.
 *
 * <p>Buckets: - "spec" : limits calls to /api/spec (Claude API, expensive) - "generate" : limits
 * STL generation
 */
@Service
public class RateLimitService {

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final int specPerHour;
    private final int generatePerHour;

    public RateLimitService(
            @Value("${app.ratelimit.spec-per-hour:20}") int specPerHour,
            @Value("${app.ratelimit.generate-per-hour:10}") int generatePerHour) {
        this.specPerHour = specPerHour;
        this.generatePerHour = generatePerHour;
    }

    public boolean allow(long userId, String bucket) {
        int limit =
                switch (bucket) {
                    case "spec" -> specPerHour;
                    case "generate" -> generatePerHour;
                    default -> 60;
                };
        long now = System.currentTimeMillis();
        long windowStart = now - 3600_000L;
        String key = userId + ":" + bucket;
        Deque<Long> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(now);
            return true;
        }
    }

    public int remaining(long userId, String bucket) {
        int limit = "spec".equals(bucket) ? specPerHour : generatePerHour;
        String key = userId + ":" + bucket;
        Deque<Long> q = buckets.get(key);
        if (q == null) return limit;
        long windowStart = System.currentTimeMillis() - 3600_000L;
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) q.pollFirst();
            return Math.max(0, limit - q.size());
        }
    }
}
