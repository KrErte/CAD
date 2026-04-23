package ee.krerte.cad.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * "Health check" Claude API jaoks — kontrollib ainult, kas API võti on seatud.
 *
 * <p>Me EI tee päris HTTP kutsumist Claude poole, kuna: 1) iga k8s readiness probe (iga ~10s) =
 * raha 2) Anthropic rate-limit'ib 3) Claude API on väljaspool meie kontrolli; kui see on maas, ei
 * ole meil mõtet pod'i teenindusest välja võtta — paremini kui backend serveerib cached vastuseid
 * või andmebaasi-baasil fallback'i.
 *
 * <p>Reegel: kui võti puudub → DOWN (sest app ei suuda mingit päringut teenindada). Kui võti olemas
 * → UP, status "configured".
 */
@Component("claudeApi")
public class ClaudeApiHealthIndicator implements HealthIndicator {

    private final String apiKey;
    private final String model;

    public ClaudeApiHealthIndicator(
            @Value("${app.claude.api-key:}") String apiKey,
            @Value("${app.claude.model:claude-opus-4-6}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Health health() {
        if (apiKey == null || apiKey.isBlank()) {
            return Health.down().withDetail("reason", "ANTHROPIC_API_KEY not configured").build();
        }
        return Health.up().withDetail("configured", true).withDetail("model", model).build();
    }
}
