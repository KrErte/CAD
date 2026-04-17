package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Talks to the Python CadQuery worker. */
@Component
public class WorkerClient {

    private final WebClient webClient;

    @Value("${app.worker.url}")
    private String workerUrl;

    public WorkerClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public JsonNode templates() {
        return webClient.get()
                .uri(workerUrl + "/templates")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    public byte[] generate(JsonNode spec) {
        return webClient.post()
                .uri(workerUrl + "/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(spec)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    /**
     * Fast heuristic metrics (volume / bbox / rough print-time) — doesn't
     * shell out to PrusaSlicer, safe to call on every slider tick.
     */
    public JsonNode metrics(JsonNode spec) {
        return webClient.post()
                .uri(workerUrl + "/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(spec)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
