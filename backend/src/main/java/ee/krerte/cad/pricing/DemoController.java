package ee.krerte.cad.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import ee.krerte.cad.WorkerClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo endpoint — no auth required.
 * IP rate-limited to 2 generations per day (SHA-256 hashed IP).
 * Template-only, no review, no meshy.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final UsageTrackingService usageTracking;
    private final WorkerClient worker;

    public DemoController(UsageTrackingService usageTracking, WorkerClient worker) {
        this.usageTracking = usageTracking;
        this.worker = worker;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody JsonNode spec, HttpServletRequest request) {
        String ip = clientIp(request);
        UsageTrackingService.CheckResult check = usageTracking.checkDemo(ip);

        if (!check.allowed()) {
            return ResponseEntity.status(429)
                    .body(
                            Map.of(
                                    "error", "demo_limit_reached",
                                    "message",
                                            "Daily demo limit reached ("
                                                    + check.limit()
                                                    + " per day). Sign up for more!",
                                    "current", check.current(),
                                    "limit", check.limit(),
                                    "upgrade_url", "/pricing"));
        }

        byte[] stl = worker.generate(spec);
        String name = spec.path("template").asText("model") + ".stl";
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/sla"))
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .body(stl);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
