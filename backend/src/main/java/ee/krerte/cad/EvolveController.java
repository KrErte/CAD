package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Darwin CAD — evolutsiooniline parameetriline disain.
 *
 * <p>See kontroller orkestreerib kolme poolt:
 *
 * <ol>
 *   <li>Kasutaja saadab eestikeelse soovi → {@link ClaudeClient#specFromPrompt} valib template'i
 *       (juba olemas).
 *   <li>Worker {@code /evolve/seed} genereerib 6 varianti (populatsioon) iga oma SVG-eelvaate,
 *       mõõtude ja ancestry-ga.
 *   <li>Claude Vision {@link ClaudeClient#rankVariants} vaatab kõiki 6 pilti korraga, reastab nad
 *       kasutaja algse soovi vastu, tagastab punktid + põhjenduse.
 * </ol>
 *
 * <p>Kasutaja valib ühe (või mitu) edasipääseja ja kutsutakse {@code /evolve/cross} järgmise
 * põlvkonna jaoks. Tsükkel jätkub kuni kasutaja on rahul.
 *
 * <p>See on tõeliselt enneolematu — Zoo, Adam, Backflip teevad KÕIK "üks-prompt-üks-vastus".
 * Darwin-loop on täiesti uus mudel.
 */
@RestController
@RequestMapping("/api/evolve")
public class EvolveController {

    private static final Logger log = LoggerFactory.getLogger(EvolveController.class);

    private final ClaudeClient claude;
    private final WorkerClient worker;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvolveController(ClaudeClient claude, WorkerClient worker) {
        this.claude = claude;
        this.worker = worker;
    }

    /**
     * Esimene põlvkond — võtab eestikeelse soovi, valib template'i (Claude), seejärel palub
     * worker'il toota 6 juhuslikku varianti + SVG-eelvaateid. Variandid hinnatakse Claude
     * Vision-iga ja tagastatakse reastatud kujul.
     *
     * <p>Kasutaja kogeb: "Ma kirjutasin 1 lause, sain 6 erinevat disaini koos põhjendusega, miks
     * üks parem kui teine." See on uus produkti-kogemus.
     */
    // BUG-FIX: sisendvalidatsioon — prompt pikkus piiratud 500 märgile
    public record SeedRequest(
            @Size(max = 500, message = "Prompt max 500 tähemärki") String prompt_et, Integer n) {}

    @PostMapping("/seed")
    public ResponseEntity<?> seed(@Valid @RequestBody SeedRequest req) {
        if (req == null || req.prompt_et() == null || req.prompt_et().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error", "prompt_required",
                                    "message", "Palun kirjuta, mida sa tahad teha."));
        }
        int n = (req.n() == null || req.n() <= 0) ? 6 : Math.min(req.n(), 12);

        // 1) Kasutaja soov → template + esialgne param-dict (seda me ei kasuta
        //    täna sampling'u jaoks, aga logime — aluseks tulevasele "bias
        //    sampling lähtepunkti ümber".)
        JsonNode catalog = worker.templates();
        JsonNode spec;
        try {
            spec = claude.specFromPrompt(req.prompt_et(), catalog);
        } catch (Exception e) {
            log.error("Claude spec failed", e);
            return ResponseEntity.status(502)
                    .body(Map.of("error", "claude_failed", "message", e.getMessage()));
        }
        if (spec.has("error")) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error", spec.get("error").asText(),
                                    "message",
                                            spec.path("summary_et")
                                                    .asText("Ei leidnud sobivat template'it")));
        }
        String template = spec.path("template").asText();

        // 2) Populatsioon workerilt
        ObjectNode evolveReq = mapper.createObjectNode();
        evolveReq.put("template", template);
        evolveReq.put("prompt_et", req.prompt_et());
        evolveReq.put("n", n);
        JsonNode population;
        try {
            population = worker.evolveSeed(evolveReq);
        } catch (Exception e) {
            log.error("Worker evolve/seed failed", e);
            return ResponseEntity.status(502)
                    .body(Map.of("error", "worker_failed", "message", e.getMessage()));
        }

        // 3) Claude Vision batch-ranking — kui API key pole, jätkame ilma
        //    reastamiseta (variandid tagastatakse worker'i järjekorras).
        JsonNode ranked;
        try {
            ranked = claude.rankVariants(req.prompt_et(), template, population);
        } catch (IllegalStateException e) {
            log.warn("Ranking disabled (no API key) — returning unranked");
            return ResponseEntity.ok(wrapUnranked(population, template, req.prompt_et()));
        } catch (Exception e) {
            log.warn("Ranking failed, returning unranked: {}", e.getMessage());
            return ResponseEntity.ok(wrapUnranked(population, template, req.prompt_et()));
        }

        return ResponseEntity.ok(
                Map.of(
                        "template", template,
                        "prompt_et", req.prompt_et(),
                        "generation", population.path("generation").asInt(1),
                        "variants", ranked, // reastatud punktide kahanemise järgi
                        "elapsed_ms", population.path("elapsed_ms").asInt(0)));
    }

    /**
     * Järgmine põlvkond — ristamine valitud vanemate (variantide) vahel + mutatsioon. Kasutaja võib
     * valida 1–3 vanemat. Seejärel sama ranking.
     */
    public record CrossRequest(
            JsonNode parents, // array Variant objektidest (workerilt)
            Integer n,
            Double mutation,
            String prompt_et) {}

    @PostMapping("/cross")
    public ResponseEntity<?> cross(@RequestBody CrossRequest req) {
        if (req == null
                || req.parents() == null
                || !req.parents().isArray()
                || req.parents().size() == 0) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "error", "parents_required",
                                    "message", "Palun vali vähemalt üks vanem."));
        }
        int n = (req.n() == null || req.n() <= 0) ? 6 : Math.min(req.n(), 12);
        double mutation =
                req.mutation() == null ? 0.2 : Math.max(0.0, Math.min(0.5, req.mutation()));

        ObjectNode crossReq = mapper.createObjectNode();
        crossReq.set("parents", req.parents());
        crossReq.put("n", n);
        crossReq.put("mutation", mutation);

        JsonNode population;
        try {
            population = worker.evolveCross(crossReq);
        } catch (Exception e) {
            log.error("Worker evolve/cross failed", e);
            return ResponseEntity.status(502)
                    .body(Map.of("error", "worker_failed", "message", e.getMessage()));
        }

        String template = req.parents().get(0).path("template").asText();
        String promptEt = req.prompt_et() == null ? "" : req.prompt_et();

        JsonNode ranked;
        try {
            ranked = claude.rankVariants(promptEt, template, population);
        } catch (Exception e) {
            log.warn("Ranking failed on cross, returning unranked: {}", e.getMessage());
            return ResponseEntity.ok(wrapUnranked(population, template, promptEt));
        }

        return ResponseEntity.ok(
                Map.of(
                        "template", template,
                        "prompt_et", promptEt,
                        "generation", population.path("generation").asInt(),
                        "variants", ranked,
                        "elapsed_ms", population.path("elapsed_ms").asInt(0)));
    }

    private Map<String, Object> wrapUnranked(
            JsonNode population, String template, String promptEt) {
        // Kui ranking ei tööta, lisame fallback-i: deterministic "score" iga
        // variandi juures heuristiliselt (väiksem overhang_risk + mõistlik mass).
        ArrayNode out = mapper.createArrayNode();
        ArrayNode pop = (ArrayNode) population.path("variants");
        int i = 0;
        for (JsonNode v : pop) {
            ObjectNode enriched = v.deepCopy();
            int score = 7 - (v.path("metrics").path("overhang_risk").asBoolean(false) ? 2 : 0);
            enriched.put("score", score);
            enriched.put(
                    "reasoning_et", "Heuristiline hinnang — AI-ülevaade oli hetkel kättesaamatu.");
            enriched.put("rank", i++);
            out.add(enriched);
        }
        return Map.of(
                "template",
                template,
                "prompt_et",
                promptEt,
                "generation",
                population.path("generation").asInt(1),
                "variants",
                out,
                "elapsed_ms",
                population.path("elapsed_ms").asInt(0),
                "ranking_source",
                "heuristic");
    }
}
