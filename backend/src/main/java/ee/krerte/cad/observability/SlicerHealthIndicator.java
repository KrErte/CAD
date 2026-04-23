package ee.krerte.cad.observability;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Readiness health check — PrusaSlicer sidecar (optional). Kui app.slicer.url on tühi (dev-setup),
 * märgime UP "disabled" meldinguga — mitte DOWN, kuna slicer on vabatahtlik komponent.
 */
@Component("slicer")
public class SlicerHealthIndicator implements HealthIndicator {

    private final String slicerUrl;
    private final WebClient client;

    public SlicerHealthIndicator(@Value("${app.slicer.url:}") String slicerUrl) {
        this.slicerUrl = slicerUrl;
        this.client =
                (slicerUrl == null || slicerUrl.isBlank())
                        ? null
                        : WebClient.builder().baseUrl(slicerUrl).build();
    }

    @Override
    public Health health() {
        if (client == null) {
            return Health.up().withDetail("status", "disabled (SLICER_URL not set)").build();
        }
        try {
            var body =
                    client.get()
                            .uri("/health")
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(2))
                            .onErrorReturn("down")
                            .block();
            if (body != null && !body.equalsIgnoreCase("down")) {
                return Health.up().withDetail("response", body).build();
            }
            return Health.down().withDetail("reason", "no-response").build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
