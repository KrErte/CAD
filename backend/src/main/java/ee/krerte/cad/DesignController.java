package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DesignController {

    private static final Logger log = LoggerFactory.getLogger(DesignController.class);

    private final ClaudeClient claude;
    private final WorkerClient worker;
    private final MeshyClient meshy;
    private final ObjectMapper mapper = new ObjectMapper();

    public DesignController(ClaudeClient claude, WorkerClient worker, MeshyClient meshy) {
        this.claude = claude;
        this.worker = worker;
        this.meshy = meshy;
    }

    /** Just expose the worker catalog (what we can design today). */
    @GetMapping("/templates")
    public JsonNode templates() {
        return worker.templates();
    }

    public record DesignRequest(@NotBlank String prompt) {}

    /** First phase: turn the Estonian text into a structured spec (no STL yet). */
    @PostMapping("/spec")
    public ResponseEntity<?> spec(@RequestBody DesignRequest req) throws Exception {
        JsonNode catalog = worker.templates();
        JsonNode spec = claude.specFromPrompt(req.prompt(), catalog);
        if (spec.has("error")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", spec.get("error").asText(),
                    "message", spec.path("summary_et").asText("Ei leidnud sobivat template'it")));
        }
        return ResponseEntity.ok(spec);
    }

    /** Second phase: generate STL from a known spec (user may have tweaked params). */
    @PostMapping(value = "/generate", produces = "application/sla")
    public ResponseEntity<byte[]> generate(@RequestBody JsonNode spec) {
        byte[] stl = worker.generate(spec);
        String name = spec.path("template").asText("model") + ".stl";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/sla"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(stl);
    }

    /**
     * Fallback: when no parametric template fits, generate a free-form mesh via Meshy.ai.
     * Returns JSON { "model_url": "..." } that the frontend loads as GLB.
     */
    @PostMapping("/meshy")
    public ResponseEntity<?> meshy(@RequestBody DesignRequest req) {
        if (!meshy.enabled()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "meshy_disabled",
                    "message", "Meshy API key pole konfigureeritud"));
        }
        try {
            String url = meshy.textTo3D(req.prompt());
            return ResponseEntity.ok(Map.of("model_url", url, "format", "glb"));
        } catch (Exception e) {
            log.error("Meshy generation failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "meshy_failed", "message", e.getMessage()));
        }
    }
}
