package ee.krerte.cad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lihtne in-memory monitoring — mõeldud admin-dashboardile.
 *
 * <p>Salvestab iga endpointi kohta viimased 1000 latentsi­mõõdet ringbuffer-is,
 * millest arvutame p50/p95/p99. Samuti loeme:
 * <ul>
 *   <li>HTTP status koodide jagunemine (2xx/4xx/5xx kaupa)</li>
 *   <li>Claude API kutsete arv + kumulatiivne latentsus</li>
 *   <li>Darwin evolve/* kutsete arv (oluline äri-metric)</li>
 *   <li>Freeform generate kutsete arv</li>
 * </ul>
 *
 * <p>Keset kasvu saab see migreerida Prometheus/Micrometer peale, aga esimestel
 * 1000 kasutajal on in-memory piisav ja null-overhead.
 *
 * <p>Endpoint: <code>GET /api/admin/metrics</code> (admin-only, kontrollib
 * AdminController õigusi).
 */
@Component
@RestController
@RequestMapping("/api/admin")
public class MetricsController implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);
    private static final int RING_SIZE = 1000;

    // endpoint → ringbuffer of latency (ms)
    private final Map<String, long[]> latencies = new ConcurrentHashMap<>();
    private final Map<String, Integer> latencyCursor = new ConcurrentHashMap<>();
    private final Map<String, Integer> latencyCount  = new ConcurrentHashMap<>();

    // endpoint → status code counters
    private final Map<String, long[]> statusCounters = new ConcurrentHashMap<>(); // [2xx, 4xx, 5xx]

    // Äri-meetrikud
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    // ── Spring MVC interceptor: kogub ajad iga päringu kohta ──────────────
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        req.setAttribute("_start_ns", System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                 Object handler, Exception ex) {
        Long start = (Long) req.getAttribute("_start_ns");
        if (start == null) return;
        long ms = (System.nanoTime() - start) / 1_000_000L;
        String path = normalizePath(req.getRequestURI());
        recordLatency(path, ms);
        recordStatus(path, res.getStatus());

        // Business counter: Darwin, freeform, generate
        if (path.startsWith("/api/evolve/")) increment("darwin_calls_total");
        else if (path.equals("/api/freeform/generate")) increment("freeform_calls_total");
        else if (path.equals("/api/generate")) increment("generate_calls_total");
        else if (path.equals("/api/spec")) increment("spec_calls_total");
    }

    private String normalizePath(String uri) {
        // Kärbi ID-d ("/api/designs/123" → "/api/designs/{id}")
        return uri.replaceAll("/\\d+(/|$)", "/{id}$1");
    }

    private void recordLatency(String path, long ms) {
        latencies.computeIfAbsent(path, k -> new long[RING_SIZE]);
        latencyCursor.putIfAbsent(path, 0);
        latencyCount.putIfAbsent(path, 0);
        synchronized (path.intern()) {
            long[] buf = latencies.get(path);
            int cur = latencyCursor.get(path);
            buf[cur] = ms;
            latencyCursor.put(path, (cur + 1) % RING_SIZE);
            latencyCount.put(path, Math.min(latencyCount.get(path) + 1, RING_SIZE));
        }
    }

    private void recordStatus(String path, int status) {
        statusCounters.computeIfAbsent(path, k -> new long[3]);
        long[] s = statusCounters.get(path);
        if (status >= 500) s[2]++;
        else if (status >= 400) s[1]++;
        else s[0]++;
    }

    public void increment(String counter) {
        counters.merge(counter, 1L, Long::sum);
    }

    public void addLatency(String counter, long ms) {
        counters.merge(counter + ":total_ms", ms, Long::sum);
        counters.merge(counter + ":count", 1L, Long::sum);
    }

    // ── Admin endpoint ────────────────────────────────────────────────────
    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        // NB: admin-check tehakse AdminController/SecurityConfig tasemel — see
        // endpoint ei tohi olla avalik! Kontrolli application.yml'is, et /api/admin/**
        // eeldab ROLE_ADMIN'i.

        Map<String, Object> out = new LinkedHashMap<>();

        // Endpointide latentsid
        Map<String, Map<String, Object>> endpoints = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>(latencies.keySet());
        paths.sort(Comparator.comparingInt(p -> -latencyCount.getOrDefault(p, 0)));
        for (String path : paths) {
            int n = latencyCount.get(path);
            if (n == 0) continue;
            long[] buf = latencies.get(path);
            long[] samples = Arrays.copyOf(buf, n);
            Arrays.sort(samples);
            long p50 = samples[(int)(n * 0.50)];
            long p95 = samples[Math.min(n - 1, (int)(n * 0.95))];
            long p99 = samples[Math.min(n - 1, (int)(n * 0.99))];
            long[] st = statusCounters.getOrDefault(path, new long[3]);
            endpoints.put(path, Map.of(
                    "count", n,
                    "p50_ms", p50,
                    "p95_ms", p95,
                    "p99_ms", p99,
                    "status_2xx", st[0],
                    "status_4xx", st[1],
                    "status_5xx", st[2]
            ));
        }
        out.put("endpoints", endpoints);
        out.put("business", counters);

        // Claude API specific — kui on logitud, arvuta avg latency
        Long ct = counters.get("claude_api_calls:total_ms");
        Long cc = counters.get("claude_api_calls:count");
        if (ct != null && cc != null && cc > 0) {
            out.put("claude_avg_latency_ms", ct / cc);
            out.put("claude_estimated_cost_eur", cc * 0.02); // heuristika: ~0.02 € kutse kohta
        }

        out.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(out);
    }
}
