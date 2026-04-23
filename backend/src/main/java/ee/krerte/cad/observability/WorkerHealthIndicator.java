package ee.krerte.cad.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Readiness health check — CadQuery worker sidecar.
 * k8s readiness probe annab pod'i Service'ist välja kui see UP → DOWN liigub.
 */
@Component("worker")
public class WorkerHealthIndicator implements HealthIndicator {

    private final WebClient client;

    public WorkerHealthIndicator(@Value("${app.worker.url:http://localhost:8000}") String workerUrl) {
        this.client = WebClient.builder().baseUrl(workerUrl).build();
    }

    @Override
    public Health health() {
        try {
            var body = client.get()
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
