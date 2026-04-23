package ee.krerte.cad.security;

import ee.krerte.cad.auth.RateLimitService;
import ee.krerte.cad.auth.User;
import ee.krerte.cad.auth.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket4j-põhine tariifi-teadlik rate limiter.
 *
 * Erinevad limit'id plaani järgi (free / maker / pro / team):
 * <ul>
 *   <li>free:  20 spec/h, 10 generate/h</li>
 *   <li>maker: 100 spec/h, 60 generate/h</li>
 *   <li>pro:   300 spec/h, 200 generate/h</li>
 *   <li>team:  unlimited (effective) — 10000/h pudelikaitseks</li>
 * </ul>
 *
 * Anonüümsete kasutajate jaoks kasutame IP-põhist rate limit'i (10/h peale
 * mõlema endpoint'i jaoks), et Claude API ei lekiks.
 *
 * Vastavad header'id:
 *   - X-RateLimit-Remaining: <n>
 *   - X-RateLimit-Limit:     <tier limit>
 *   - Retry-After:           <seconds until next refill>  (kui 429)
 *
 * Märkus: in-memory cache — horizontaalne skaleerumine vajab Redis
 * (bucket4j-redis library). See on järgmine samm db-infrastructure branch'is.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Per-plan limiidid (calls per hour)
    private final Map<String, TierLimits> tiers = Map.of(
        "anonymous", new TierLimits(10, 5),
        "free",      new TierLimits(20, 10),
        "maker",     new TierLimits(100, 60),
        "pro",       new TierLimits(300, 200),
        "team",      new TierLimits(10_000, 10_000)
    );

    public RateLimitFilter(UserRepository users,
                           @Value("${app.ratelimit.spec-per-hour:20}") int freeSpec,
                           @Value("${app.ratelimit.generate-per-hour:10}") int freeGenerate) {
        this.users = users;
        // Override free tier config'ist (taga-ühilduv olemasoleva config'uga)
        this.tiers.put("free", new TierLimits(freeSpec, freeGenerate));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        // Limit'ime ainult kalleid endpoint'e. Ülejäänud passthrough.
        return !(uri.startsWith("/api/spec")
              || uri.startsWith("/api/generate")
              || uri.startsWith("/api/review")
              || uri.startsWith("/api/evolve")
              || uri.startsWith("/api/freeform"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String uri = req.getRequestURI();
        String bucketType = uri.startsWith("/api/spec") ? "spec" : "generate";

        IdentityAndTier id = identify(req);
        TierLimits limits = tiers.getOrDefault(id.tier(), tiers.get("anonymous"));
        int limit = bucketType.equals("spec") ? limits.spec() : limits.generate();

        String key = id.identifier() + ":" + bucketType;
        Bucket bucket = buckets.computeIfAbsent(key,
                k -> Bucket.builder().addLimit(Bandwidth.simple(limit, Duration.ofHours(1))).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        resp.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        resp.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));

        if (!probe.isConsumed()) {
            long retryAfterSec = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            resp.setHeader("Retry-After", String.valueOf(retryAfterSec));
            resp.setStatus(429);
            resp.setContentType("application/json");
            resp.getWriter().write(String.format(
                "{\"error\":\"rate_limited\",\"bucket\":\"%s\",\"tier\":\"%s\",\"retryAfterSec\":%d}",
                bucketType, id.tier(), retryAfterSec));
            return;
        }
        chain.doFilter(req, resp);
    }

    private IdentityAndTier identify(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            Optional<User> user = users.findByEmail(email);
            if (user.isPresent()) {
                return new IdentityAndTier("u:" + user.get().getId(), resolveTier(user.get()));
            }
        }
        // Anonüümne — IP (proxy-teadlik)
        String ip = resolveClientIp(req);
        return new IdentityAndTier("ip:" + ip, "anonymous");
    }

    /** Kuna plaan on kasutajaobjektis — mappime selle tier'i kategooriaks. */
    private String resolveTier(User user) {
        // Eeldab, et User.plan() return'ib "free" / "maker" / "pro" / "team" / "hobi"
        // Hobi on legacy alias maker'i jaoks (vt docs/STRATEGY-2026).
        try {
            var m = user.getClass().getMethod("getPlan");
            Object plan = m.invoke(user);
            if (plan == null) return "free";
            String p = plan.toString().toLowerCase();
            if (p.equals("hobi")) return "maker";
            return p;
        } catch (Exception e) {
            return "free";
        }
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return req.getRemoteAddr();
    }

    private record IdentityAndTier(String identifier, String tier) {}

    private record TierLimits(int spec, int generate) {}
}
