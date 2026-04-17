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
    private final SlicerClient slicer;
    private final ObjectMapper mapper = new ObjectMapper();

    public DesignController(ClaudeClient claude, WorkerClient worker, MeshyClient meshy, SlicerClient slicer) {
        this.claude = claude;
        this.worker = worker;
        this.meshy = meshy;
        this.slicer = slicer;
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

    /**
     * Fast, free metrics endpoint — passes through to the worker's heuristic.
     * Called on every slider change so it must stay cheap (no slicing here).
     */
    @PostMapping("/metrics")
    public ResponseEntity<?> metrics(@RequestBody JsonNode spec) {
        try {
            return ResponseEntity.ok(worker.metrics(spec));
        } catch (Exception e) {
            log.warn("Worker /metrics failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", "worker_failed", "message", e.getMessage()));
        }
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
     * Cost/time preview: generate STL internally, slice it with PrusaSlicer
     * (sidecar), return { print_time_sec, filament_g, filament_cost_eur, ... }.
     *
     * Gracefully degrades: if the slicer sidecar isn't configured or fails,
     * we fall back to the worker's volume-based heuristic so the UI always
     * gets SOME answer. The response includes a "source" field so the
     * frontend can tell whether the numbers are "exact" (slicer) or
     * "estimate" (heuristic).
     */
    public record PreviewRequest(@NotBlank String template,
                                 JsonNode params,
                                 String material,
                                 String fillDensity,
                                 String layerHeight) {}

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody JsonNode body) {
        String preset = preset(body);
        String fillDensity = textOrNull(body, "fillDensity");
        String layerHeight = textOrNull(body, "layerHeight");

        // Build the worker-shaped spec (template + params) from the incoming body.
        if (!body.hasNonNull("template")) {
            return ResponseEntity.badRequest().body(Map.of("error", "template_required"));
        }

        // --- Always run the fast heuristic first (gives frontend a snappy answer
        //     even if the slicer is busy or unavailable).
        JsonNode heuristic;
        try {
            heuristic = worker.metrics(body);
        } catch (Exception e) {
            log.warn("Worker /metrics failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", "worker_failed",
                    "message", e.getMessage()));
        }

        // --- If the slicer isn't enabled, return only the heuristic.
        if (!slicer.enabled()) {
            return ResponseEntity.ok(wrapHeuristic(heuristic, "heuristic"));
        }

        // --- Slicer path: produce STL then slice it.
        byte[] stl;
        try {
            stl = worker.generate(body);
        } catch (Exception e) {
            log.warn("Worker /generate failed: {}", e.getMessage());
            return ResponseEntity.ok(wrapHeuristic(heuristic, "heuristic"));
        }

        try {
            JsonNode slicerResp = slicer.slice(stl, preset, fillDensity, layerHeight);
            return ResponseEntity.ok(mergeSlicerAndHeuristic(slicerResp, heuristic, preset));
        } catch (Exception e) {
            log.warn("Slicer failed, falling back to heuristic: {}", e.getMessage());
            return ResponseEntity.ok(wrapHeuristic(heuristic, "heuristic_slicer_failed"));
        }
    }

    private String preset(JsonNode body) {
        String material = textOrNull(body, "material");
        if (material == null || material.isBlank()) return "pla_default";
        return switch (material.toLowerCase()) {
            case "petg" -> "petg_default";
            default -> "pla_default";
        };
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private Map<String, Object> wrapHeuristic(JsonNode heuristic, String source) {
        return Map.of(
                "source", source,
                "print_time_sec", heuristic.path("print_time_min_estimate").asInt() * 60,
                "print_time_human", heuristic.path("print_time_min_estimate").asInt() + " min",
                "filament_g", heuristic.path("weight_g_pla").asDouble(),
                "filament_cost_eur", round2(heuristic.path("weight_g_pla").asDouble() / 1000.0 * 25.0),
                "volume_cm3", heuristic.path("volume_cm3").asDouble(),
                "bbox_mm", heuristic.path("bbox_mm"),
                "overhang_risk", heuristic.path("overhang_risk").asBoolean(false)
        );
    }

    private Map<String, Object> mergeSlicerAndHeuristic(JsonNode slicerResp, JsonNode heuristic, String preset) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("source", "slicer");
        out.put("preset", preset);
        out.put("print_time_sec", slicerResp.path("print_time_sec").asInt());
        out.put("print_time_human", slicerResp.path("print_time_human").asText());
        out.put("filament_length_m", slicerResp.path("filament_length_m").asDouble());
        out.put("filament_volume_cm3", slicerResp.path("filament_volume_cm3").asDouble());
        out.put("filament_g", slicerResp.path("filament_g").asDouble());
        out.put("filament_cost_eur", slicerResp.path("filament_cost_eur").asDouble());
        // Keep bbox + overhang from the worker — slicer doesn't expose them.
        out.put("volume_cm3", heuristic.path("volume_cm3").asDouble());
        out.put("bbox_mm", heuristic.path("bbox_mm"));
        out.put("overhang_risk", heuristic.path("overhang_risk").asBoolean(false));
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
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
