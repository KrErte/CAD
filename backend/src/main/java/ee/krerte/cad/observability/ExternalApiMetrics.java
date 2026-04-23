package ee.krerte.cad.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Ühtne fasaad kõigile välja-minevatele HTTP-kutsetele — worker, slicer, Meshy, partnerite pricing
 * API-d. Registreerib:
 *
 * <ul>
 *   <li>{@code external.api.duration{service, endpoint, status}} — timer
 *   <li>{@code external.api.requests.total{service, endpoint, status}} — counter
 * </ul>
 *
 * Kasuta klienti nii:
 *
 * <pre>
 *   metrics.time("worker", "/generate", () -> webClient.post()...)
 * </pre>
 */
@Component
public class ExternalApiMetrics {

    private final MeterRegistry registry;

    public ExternalApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T time(String service, String endpoint, Supplier<T> operation) {
        long start = System.nanoTime();
        String status = "success";
        try {
            return operation.get();
        } catch (RuntimeException e) {
            status = classifyError(e);
            throw e;
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            registry.timer(
                            "external.api.duration",
                            "service",
                            service,
                            "endpoint",
                            endpoint,
                            "status",
                            status)
                    .record(elapsed);
            registry.counter(
                            "external.api.requests.total",
                            "service",
                            service,
                            "endpoint",
                            endpoint,
                            "status",
                            status)
                    .increment();
        }
    }

    public void record(String service, String endpoint, long durationMs, String status) {
        registry.timer(
                        "external.api.duration",
                        "service",
                        service,
                        "endpoint",
                        endpoint,
                        "status",
                        status)
                .record(Duration.ofMillis(durationMs));
        registry.counter(
                        "external.api.requests.total",
                        "service",
                        service,
                        "endpoint",
                        endpoint,
                        "status",
                        status)
                .increment();
    }

    private String classifyError(Throwable e) {
        String msg = e.getClass().getSimpleName().toLowerCase();
        if (msg.contains("timeout")) return "timeout";
        if (msg.contains("connect")) return "connect_error";
        return "error";
    }
}
