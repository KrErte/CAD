package ee.krerte.cad.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.krerte.cad.ClaudeClient;
import ee.krerte.cad.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orkestreerib multi-agent disaininõukogu: 4 spetsialisti → Synthesizer.
 *
 * <p><b>Kuidas see erineb tavalisest Claude-review'st?</b>
 * <ul>
 *   <li>Tava-review: ÜKS Claude-kutse, ÜKS generalist-hääl. Hea baasi jaoks,
 *       aga peidab valede trade-offide vahelisi konflikte.</li>
 *   <li>Multi-agent: 4 PARALLEELSET kutset, iga oma kitsa ekspertiisiga.
 *       Kui struktuur-insener ja maksumuse-optimeerija on <em>eri meelt</em>
 *       (insener tahab 10mm seina, optimeerija 4mm), näeb kasutaja seda
 *       konflikti otseselt — mitte peidetult LLM-i "keskmistatud" vastuses.</li>
 * </ul>
 *
 * <p><b>Paralleelsus:</b> 4 HTTP-kutset Anthropicule {@code parallelism=4}
 * fixed pool'is. Keskmise latentsuse (~3s per call) juures teeb see
 * ~3.5s kokku 12s asemel. Synthesizer järgneb sekventsiaalselt.
 *
 * <p><b>DFM eel-audit:</b> enne LLM-kutseid jooksutame worker'is
 * deterministliku reegli-põhise DFM-analüüsi (~50ms). Selle tulemused
 * antakse IGA agendile ette, et LLM-id ei peaks ise üldtuntud FDM-reegleid
 * avastama.
 */
@Service
public class MultiAgentReviewService {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentReviewService.class);

    private final ClaudeClient claude;
    private final WorkerClient worker;
    private final ObjectMapper mapper = new ObjectMapper();

    // 4 paralleelset Claude-kutset — üks iga persona kohta
    private final ExecutorService exec = Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "multi-agent-review");
                t.setDaemon(true);
                return t;
            });

    public MultiAgentReviewService(ClaudeClient claude, WorkerClient worker) {
        this.claude = claude;
        this.worker = worker;
    }

    /**
     * Jookseta kogu nõukogu: 4 agenti paralleelselt + synthesizer.
     *
     * @param userPromptEt   kasutaja originaal eestikeelne soov
     * @param spec           resolveeritud spec (template + params)
     * @param imageBase64Png three.js preview PNG (võib olla null)
     * @return JSON: { dfm:{...}, agents:[{persona,score,verdict_et,findings[],suggestions[]}, ...],
     *                 synthesis:{overall, verdict_et, top_actions[]},
     *                 council_score (kaalutud 1.00–10.00) }
     */
    public ObjectNode runCouncil(String userPromptEt, JsonNode spec, String imageBase64Png) {
        ObjectNode result = mapper.createObjectNode();

        // ---- 1. Rule-based DFM eelaudit (deterministlik, < 100ms) ----
        JsonNode dfm = null;
        try {
            dfm = worker.dfmAnalyze(spec);
            result.set("dfm", dfm);
        } catch (Exception e) {
            log.warn("DFM eelaudit ebaõnnestus, jätkame ilma: {}", e.getMessage());
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "dfm_failed");
            err.put("message", e.getMessage());
            result.set("dfm", err);
        }

        // ---- 2. 4 agenti paralleelselt ----
        final JsonNode dfmFinal = dfm;
        AgentPersona[] personas = AgentPersona.values();
        List<CompletableFuture<JsonNode>> futures = new ArrayList<>();
        for (AgentPersona p : personas) {
            CompletableFuture<JsonNode> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return claude.reviewAsPersona(p, userPromptEt, spec, dfmFinal, imageBase64Png);
                } catch (Exception e) {
                    log.warn("Persona {} ebaõnnestus: {}", p.code(), e.getMessage());
                    ObjectNode err = mapper.createObjectNode();
                    err.put("persona", p.code());
                    err.put("persona_display", p.displayNameEt());
                    err.put("weight", p.weight());
                    err.put("error", e.getMessage());
                    err.put("score", 5);
                    err.put("verdict_et", "Agent ei suutnud vastata: " + e.getMessage());
                    err.putArray("findings");
                    err.putArray("suggestions");
                    return err;
                }
            }, exec);
            futures.add(f);
        }

        ArrayNode agents = mapper.createArrayNode();
        double weightedSum = 0.0;
        double weightTotal = 0.0;
        for (CompletableFuture<JsonNode> f : futures) {
            JsonNode resp = f.join();
            agents.add(resp);
            double w = resp.path("weight").asDouble(0.0);
            int s = resp.path("score").asInt(0);
            if (s > 0 && w > 0) {
                weightedSum += s * w;
                weightTotal += w;
            }
        }
        result.set("agents", agents);
        double council = weightTotal > 0 ? weightedSum / weightTotal : 5.0;
        result.put("council_score", Math.round(council * 100.0) / 100.0);

        // ---- 3. Synthesizer ----
        try {
            JsonNode synth = claude.synthesizeCouncil(userPromptEt, spec, agents);
            result.set("synthesis", synth);
        } catch (Exception e) {
            log.warn("Synthesizer ebaõnnestus: {}", e.getMessage());
            ObjectNode synth = mapper.createObjectNode();
            synth.put("overall", council >= 7 ? "ship_it" : (council >= 4 ? "iterate" : "redesign"));
            synth.put("verdict_et", "Synthesizer ei saanud vastust; kasutan kaalutud keskmist.");
            synth.putArray("top_actions");
            result.set("synthesis", synth);
        }

        return result;
    }
}
