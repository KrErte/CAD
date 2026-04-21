package ee.krerte.cad.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Claude API kulu + kasutuse meetrikad.
 *
 * Igal Claude kutsumisel ({@code ClaudeClient.call(...)} jne) peaks kutsuma
 * {@link #recordUsage(String, long, long, long)} — see registreerib:
 * <ul>
 *   <li>{@code claude.api.tokens.input}   — counter per mudel</li>
 *   <li>{@code claude.api.tokens.output}  — counter per mudel</li>
 *   <li>{@code claude.api.duration}       — timer (histogram p50/p95/p99)</li>
 *   <li>{@code claude.api.cost.eur}       — counter (€ summa, arvutatud
 *       input/output token count'ist * app.pricing-claude.*)</li>
 *   <li>{@code claude.api.requests.total} — counter per mudel + staatus</li>
 * </ul>
 *
 * Prometheus scrape'ib need, Grafana dashboard näitab:
 * "Claude € täna" = increase(claude_api_cost_eur_total[24h])
 * "Input token/s"  = rate(claude_api_tokens_input_total[5m])
 */
@Component
public class ClaudeCostMetrics {

    private final MeterRegistry registry;
    private final double inputEurPerMtok;
    private final double outputEurPerMtok;

    public ClaudeCostMetrics(
            MeterRegistry registry,
            @Value("${app.pricing-claude.input-eur-per-mtok:13.5}") double inputEurPerMtok,
            @Value("${app.pricing-claude.output-eur-per-mtok:67.5}") double outputEurPerMtok
    ) {
        this.registry = registry;
        this.inputEurPerMtok = inputEurPerMtok;
        this.outputEurPerMtok = outputEurPerMtok;
    }

    /**
     * Registreeri Claude API kutsumine — kutsu peale igat edukat vastust.
     *
     * @param model       nt "claude-opus-4-6"
     * @param inputTokens {@code usage.input_tokens} API vastusest
     * @param outputTokens {@code usage.output_tokens} API vastusest
     * @param durationMs  kutse kestus millisekundites
     */
    public void recordUsage(String model, long inputTokens, long outputTokens, long durationMs) {
        if (model == null || model.isBlank()) model = "unknown";

        registry.counter("claude.api.tokens.input", "model", model).increment(inputTokens);
        registry.counter("claude.api.tokens.output", "model", model).increment(outputTokens);

        double costEur = (inputTokens / 1_000_000.0) * inputEurPerMtok
                       + (outputTokens / 1_000_000.0) * outputEurPerMtok;
        registry.counter("claude.api.cost.eur", "model", model).increment(costEur);

        registry.timer("claude.api.duration", "model", model, "status", "success")
                .record(java.time.Duration.ofMillis(durationMs));

        registry.counter("claude.api.requests.total",
                "model", model, "status", "success").increment();
    }

    /** Ebaõnnestunud kutse — peamiselt timeout, 5xx, rate-limit. */
    public void recordFailure(String model, String errorType, long durationMs) {
        if (model == null || model.isBlank()) model = "unknown";
        if (errorType == null) errorType = "unknown";

        registry.timer("claude.api.duration", "model", model, "status", "failure")
                .record(java.time.Duration.ofMillis(durationMs));

        registry.counter("claude.api.requests.total",
                "model", model, "status", "failure", "error", errorType).increment();
    }
}
