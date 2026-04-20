package ee.krerte.cad.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.krerte.cad.ClaudeClient;
import ee.krerte.cad.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Generatiivne disaini-silmus — "iterate until perfect".
 *
 * <p>Silmus:
 * <pre>
 *   t = 0:
 *     review = singleReview(spec)            // 1x Claude call
 *     if review.score &gt;= TARGET → stop (target_reached)
 *   t = 1..MAX_ITER:
 *     patch  = pickBestSuggestion(review)    // valib suurima new_value'ga soovituse
 *     spec'  = applyPatch(spec, patch)       // spec.params[param] = new_value
 *     review = singleReview(spec')           // 1x Claude call
 *     if review.score &gt;= TARGET → stop (target_reached)
 *     if review.score &lt;= prev_score - 1 → stop (no_improvement / regressing)
 * </pre>
 *
 * <p>Iga samm emiteeritakse {@link SseEmitter}-ina frontendile, et kasutaja
 * näeks reaalajas, kuidas disain konvergeerub. "Magic-moment" UX.
 *
 * <p><b>Turvamehhanismid:</b>
 * <ul>
 *   <li>MAX_ITER = 5 — ei saa lõputult Anthropic'u krediiti kulutada.</li>
 *   <li>no_improvement — kui 2 järjestikust sammu score langes, lõpetame.</li>
 *   <li>spec-clamp — enne apply-patchi võtame template skeemist min/max ja
 *       clamp'ime new_value (vastasel juhul LLM võib soovitada väärtust,
 *       mis ei ole skeemi sees → worker crashib).</li>
 * </ul>
 */
@Service
public class GenerativeLoopService {

    private static final Logger log = LoggerFactory.getLogger(GenerativeLoopService.class);

    public static final int MAX_ITER = 5;
    public static final double DEFAULT_TARGET_SCORE = 8.5;

    private final ClaudeClient claude;
    private final WorkerClient worker;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenerativeLoopService(ClaudeClient claude, WorkerClient worker) {
        this.claude = claude;
        this.worker = worker;
    }

    /**
     * Jookseta silmus ja anna edasi sündmused {@code emit}-consumer'ile.
     *
     * <p>Iga event on JSON ObjectNode ühes järgnevatest vormingutest:
     * <ul>
     *   <li>{@code {"type":"start", "target":8.5, "initial_spec":{...}}}</li>
     *   <li>{@code {"type":"review", "step":N, "score":7.3, "verdict_et":"..."}}</li>
     *   <li>{@code {"type":"patch", "step":N, "param":"wall_thickness", "old":3, "new":5, "rationale_et":"..."}}</li>
     *   <li>{@code {"type":"stop", "reason":"target_reached|max_iter|no_improvement", "final_score":..., "final_spec":{...}, "steps":N}}</li>
     * </ul>
     *
     * @param userPromptEt originaal kasutaja soov (review'sse läheb)
     * @param initialSpec  algne spec (template + params)
     * @param targetScore  skoor, mille saavutamisel lõpetame (nt 8.5)
     * @param emit         callback iga event'i jaoks
     */
    public void iterate(String userPromptEt,
                        JsonNode initialSpec,
                        double targetScore,
                        Consumer<ObjectNode> emit) {
        // Küsi worker'ilt template kataloog, et saaksime param'eid clamp'ida
        JsonNode catalog;
        try {
            catalog = worker.templates();
        } catch (Exception e) {
            log.warn("Templates fetch ebaõnnestus: {}", e.getMessage());
            catalog = mapper.createObjectNode();
        }

        double target = targetScore > 0 ? targetScore : DEFAULT_TARGET_SCORE;
        ObjectNode start = mapper.createObjectNode();
        start.put("type", "start");
        start.put("target", target);
        start.set("initial_spec", initialSpec);
        emit.accept(start);

        ObjectNode currentSpec = (ObjectNode) initialSpec.deepCopy();
        double prevScore = -1;
        int regressions = 0;
        String stopReason = "max_iter";
        JsonNode lastReview = null;

        for (int step = 0; step < MAX_ITER; step++) {
            // 1. Review
            JsonNode review;
            try {
                // iterate'i jaoks kasutame single-agent review'd — kiirem, odavam.
                // Multi-agent on eraldi endpoint kui kasutaja tahab sügavat auditit.
                review = claude.reviewDesign(userPromptEt, currentSpec, null);
            } catch (Exception e) {
                log.warn("Review step={} ebaõnnestus: {}", step, e.getMessage());
                ObjectNode err = mapper.createObjectNode();
                err.put("type", "error");
                err.put("step", step);
                err.put("message", e.getMessage());
                emit.accept(err);
                stopReason = "review_failed";
                break;
            }
            lastReview = review;

            ObjectNode revEvent = mapper.createObjectNode();
            revEvent.put("type", "review");
            revEvent.put("step", step);
            revEvent.put("score", review.path("score").asInt(5));
            revEvent.put("verdict_et", review.path("verdict_et").asText(""));
            revEvent.set("spec", currentSpec);
            emit.accept(revEvent);

            double score = review.path("score").asDouble(5.0);

            // 2. Kas oleme target'il?
            if (score >= target) {
                stopReason = "target_reached";
                break;
            }

            // 3. Regressiooni-kontroll
            if (prevScore > 0 && score < prevScore - 1.0) {
                regressions++;
                if (regressions >= 1) {
                    // Üks regressioon on piisav signaal — viimane patch halvendas.
                    // Lõpetame ja tagastame eelmise (parima) spec'i.
                    stopReason = "no_improvement";
                    break;
                }
            } else {
                regressions = 0;
            }
            prevScore = score;

            // 4. Vali parim rakendatav soovitus
            JsonNode patch = pickActionablePatch(review, currentSpec, catalog);
            if (patch == null) {
                stopReason = "no_patch_available";
                break;
            }

            // 5. Rakenda patch
            String param = patch.path("param").asText();
            double newValue = patch.path("new_value").asDouble();
            double clamped = clampToSchema(catalog, currentSpec.path("template").asText(),
                                            param, newValue);
            JsonNode oldValue = currentSpec.path("params").path(param);
            ObjectNode params = (ObjectNode) currentSpec.get("params");
            if (params == null) {
                params = currentSpec.putObject("params");
            }
            params.put(param, clamped);

            ObjectNode patchEvent = mapper.createObjectNode();
            patchEvent.put("type", "patch");
            patchEvent.put("step", step);
            patchEvent.put("param", param);
            patchEvent.set("old", oldValue);
            patchEvent.put("new", clamped);
            patchEvent.put("rationale_et", patch.path("rationale_et").asText(""));
            patchEvent.put("label_et", patch.path("label_et").asText(""));
            emit.accept(patchEvent);
        }

        ObjectNode stop = mapper.createObjectNode();
        stop.put("type", "stop");
        stop.put("reason", stopReason);
        stop.put("final_score", lastReview == null ? 0 : lastReview.path("score").asInt(5));
        stop.set("final_spec", currentSpec);
        if (lastReview != null) {
            stop.put("final_verdict_et", lastReview.path("verdict_et").asText(""));
        }
        emit.accept(stop);
    }

    /**
     * Vali review-vastusest üks konkreetne, rakendatav (param + new_value)
     * soovitus. Kui mitmekesi, eelista seda, mis kõige suurema potentsiaaliga
     * — kõige suurem diff praegusest väärtusest näitab, et LLM peab seda
     * tähtsaks muudatuseks.
     */
    private JsonNode pickActionablePatch(JsonNode review, JsonNode currentSpec, JsonNode catalog) {
        JsonNode suggestions = review.path("suggestions");
        if (!suggestions.isArray() || suggestions.isEmpty()) return null;

        JsonNode best = null;
        double bestDelta = -1;
        for (JsonNode s : suggestions) {
            if (!s.hasNonNull("param") || !s.hasNonNull("new_value")) continue;
            String p = s.path("param").asText();
            double nv = s.path("new_value").asDouble();
            JsonNode cur = currentSpec.path("params").path(p);
            if (cur.isMissingNode() || !cur.isNumber()) {
                // LLM soovitas parameetrit, mis praegu spec'is pole — võtame seda
                // null-start'ist, delta = |new_value|
                double delta = Math.abs(nv);
                if (delta > bestDelta) { best = s; bestDelta = delta; }
            } else {
                double delta = Math.abs(nv - cur.asDouble());
                if (delta > bestDelta) { best = s; bestDelta = delta; }
            }
        }
        return best;
    }

    /**
     * Võta worker'i template catalog'ist min/max piirid ja clamp'i new_value
     * nende sisse. Kui piire pole (vabad param'id) → tagasta muutmata.
     */
    private double clampToSchema(JsonNode catalog, String template, String param, double value) {
        JsonNode schema = catalog.path(template).path("params").path(param);
        if (schema.isMissingNode()) return value;
        double min = schema.path("min").asDouble(Double.NEGATIVE_INFINITY);
        double max = schema.path("max").asDouble(Double.POSITIVE_INFINITY);
        return Math.max(min, Math.min(max, value));
    }
}
