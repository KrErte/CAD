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

    /** STEP-i eksport — insener-tasandi B-Rep fail. Täiendab STL väljundi. */
    public byte[] generateStep(JsonNode spec) {
        return webClient.post()
                .uri(workerUrl + "/generate_step")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(spec)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    // --- Darwin CAD --------------------------------------------------------

    /** Esimese põlvkonna populatsioon — 6–8 varianti SVG-eelvaatega. */
    public JsonNode evolveSeed(JsonNode body) {
        return webClient.post()
                .uri(workerUrl + "/evolve/seed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /** Järgmine põlvkond — vanemate ristamine ja mutatsioon. */
    public JsonNode evolveCross(JsonNode body) {
        return webClient.post()
                .uri(workerUrl + "/evolve/cross")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // --- Freeform script-gen -----------------------------------------------

    /**
     * Saadab LLM-genereeritud CadQuery Pythoni koodi sandbox-worker'ile.
     * Worker tagastab JSON-i {ok, error, files:{stl,step}, elapsed_ms}.
     */
    public JsonNode freeformGenerate(JsonNode body) {
        return webClient.post()
                .uri(workerUrl + "/freeform/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // --- DFM Analyzer ------------------------------------------------------

    /**
     * Reeglipõhine DFM-audit: thin walls, overhang'id, bridge'id, min-feature,
     * footprint. Vastab &lt; 100ms ja on deterministlik — mõeldud jooksutamaks
     * ENNE Claude-kutset, et LLM saaks töötada faktiliste avastuste pealt
     * mitte nendega, mida ta peab ise arvama.
     */
    public JsonNode dfmAnalyze(JsonNode spec) {
        return webClient.post()
                .uri(workerUrl + "/dfm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(spec)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
