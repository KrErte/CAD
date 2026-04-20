package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fallback path: when no parametric template fits, we ask Meshy.ai to mesh-generate a
 * model from the user prompt (or an image). Returns a downloadable GLB / STL URL.
 *
 * Free credits available; paid plans start at ~$20/mo. Disabled if MESHY_API_KEY missing.
 *
 * Docs: https://docs.meshy.ai/
 */
@Component
public class MeshyClient {

    private static final Logger log = LoggerFactory.getLogger(MeshyClient.class);
    private static final String BASE = "https://api.meshy.ai/openapi/v2";

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // BUG-FIX: eraldi threadpool Meshy pollingu jaoks, et Tomcat servlet-threadid ei blokeeruks.
    // Varem oli Thread.sleep() otse request-threadil → koormusel ~60 samaaegset päringut
    // ammendaks kogu Tomcat pooli ja server ripuks.
    private final Executor meshyExecutor = Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "meshy-poll"); t.setDaemon(true); return t; });

    @Value("${app.meshy.api-key:}") private String apiKey;

    public MeshyClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Kicks off a text-to-3D job, polls until ready, returns the model URL.
     *
     * BUG-FIX: polling toimub nüüd eraldi ExecutorService'is (meshyExecutor),
     * mitte Tomcat servlet-threadil. See vabastab servlet-threadi kohe ja
     * caller saab CompletableFuture kaudu tulemuse kätte.
     */
    public CompletableFuture<String> textTo3DAsync(String prompt) {
        if (!enabled()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("MESHY_API_KEY not configured"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return textTo3DBlocking(prompt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Meshy polling interrupted", e);
            }
        }, meshyExecutor);
    }

    /** Sünkroonne variant — kutsutakse ainult meshyExecutor threadist, MITTE Tomcat threadist. */
    private String textTo3DBlocking(String prompt) throws InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("mode", "preview");
        body.put("prompt", prompt);
        body.put("art_style", "realistic");

        JsonNode createResp = webClient.post()
                .uri(BASE + "/text-to-3d")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        // BUG-FIX: null-check enne nested field ligipääsu — API võib tagastada ootamatu vastuse
        if (createResp == null || !createResp.has("result")) {
            throw new RuntimeException("Meshy create response missing 'result': " + createResp);
        }
        String id = createResp.get("result").asText();
        log.info("Meshy job created: {}", id);

        for (int i = 0; i < 60; i++) { // up to ~5 min
            Thread.sleep(5_000);
            JsonNode status = webClient.get()
                    .uri(BASE + "/text-to-3d/" + id)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String s = (status != null) ? status.path("status").asText() : "UNKNOWN";
            log.debug("Meshy {} status: {}", id, s);
            if ("SUCCEEDED".equals(s)) {
                // model_urls.glb / .obj / .fbx — STL is not native, frontend converts via three.js
                return status.path("model_urls").path("glb").asText();
            }
            if ("FAILED".equals(s)) {
                throw new RuntimeException("Meshy job failed: " + status.toString());
            }
        }
        throw new RuntimeException("Meshy job timeout");
    }
}
