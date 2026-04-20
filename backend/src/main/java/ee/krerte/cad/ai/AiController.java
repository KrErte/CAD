package ee.krerte.cad.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.krerte.cad.ClaudeClient;
import ee.krerte.cad.WorkerClient;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executors;

/**
 * AI Superpowers kontroller — eksponeerib uued AI-kihid eraldi
 * {@code /api/ai/*} namespace'i alla.
 *
 * <ul>
 *   <li>{@code POST /api/ai/review-council} — multi-agent disaininõukogu</li>
 *   <li>{@code POST /api/ai/iterate} — generative loop (SSE stream)</li>
 *   <li>{@code POST /api/ai/dfm} — rule-based DFM audit (proxy worker'ile)</li>
 *   <li>{@code POST /api/ai/fix} — rule-based DFM + LLM soovitus ühes vastuses</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final MultiAgentReviewService council;
    private final GenerativeLoopService loop;
    private final WorkerClient worker;
    private final ClaudeClient claude;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiController(MultiAgentReviewService council,
                        GenerativeLoopService loop,
                        WorkerClient worker,
                        ClaudeClient claude) {
        this.council = council;
        this.loop = loop;
        this.worker = worker;
        this.claude = claude;
    }

    public record CouncilRequest(JsonNode spec, String prompt_et, String image_base64) {}

    /**
     * Jooksuta 4-agendi nõukogu + synthesizer.
     * Kõrge-latentse (~4s + 4s) kutse, aga teeb seda, mida üks Claude-review
     * ei suuda: toob välja <em>konfliktid</em> spetsialistide vahel.
     */
    @PostMapping("/review-council")
    public ResponseEntity<?> reviewCouncil(@RequestBody CouncilRequest req) {
        if (req == null || req.spec() == null || !req.spec().hasNonNull("template")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "spec_required",
                    "message", "Vajan 'spec' väljal vähemalt template'it ja param'eid."));
        }
        try {
            ObjectNode result = council.runCouncil(req.prompt_et(), req.spec(), req.image_base64());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "ai_disabled",
                    "message", "Claude API võti pole konfigureeritud."));
        } catch (Exception e) {
            log.error("Council ebaõnnestus", e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "council_failed",
                    "message", e.getMessage()));
        }
    }

    public record IterateRequest(@NotNull JsonNode spec,
                                 String prompt_et,
                                 Double target_score) {}

    /**
     * Generative loop — SSE stream.
     *
     * <p>Kuidas testida curl'iga:
     * <pre>
     *   curl -N -X POST http://localhost:8080/api/ai/iterate \
     *        -H 'Content-Type: application/json' \
     *        -d '{"spec":{"template":"shelf_bracket","params":{"load_kg":10,"wall_thickness":2}},
     *             "prompt_et":"tugev klamber 10kg koormusele", "target_score": 8.5}'
     * </pre>
     *
     * <p>Iga sündmus emiteeritakse kui {@code event:<type>\ndata:<json>\n\n}.
     */
    @PostMapping(value = "/iterate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter iterate(@RequestBody IterateRequest req) {
        // 5 min timeout — loop peaks max ~30s võtma, aga slower model + retry...
        SseEmitter emitter = new SseEmitter(300_000L);

        // Jookseta taustalõimes et mitte hoida Tomcat threadi
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "generative-loop");
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            try {
                double target = req.target_score() != null ? req.target_score() :
                        GenerativeLoopService.DEFAULT_TARGET_SCORE;
                loop.iterate(req.prompt_et(), req.spec(), target, event -> {
                    try {
                        String eventName = event.path("type").asText("message");
                        emitter.send(SseEmitter.event()
                                .name(eventName)
                                .data(event.toString(), MediaType.APPLICATION_JSON));
                    } catch (Exception e) {
                        log.warn("SSE send failed: {}", e.getMessage());
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Generative loop crashed", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Otse-proxy worker'i DFM-auditile. Kasutaja kirjutab spec'i, see
     * tagastab reeglipõhised issued KOHE (~50ms, ilma LLM-iga).
     */
    @PostMapping("/dfm")
    public ResponseEntity<?> dfm(@RequestBody JsonNode spec) {
        if (spec == null || !spec.hasNonNull("template")) {
            return ResponseEntity.badRequest().body(Map.of("error", "template_required"));
        }
        try {
            return ResponseEntity.ok(worker.dfmAnalyze(spec));
        } catch (Exception e) {
            log.warn("DFM analyze ebaõnnestus: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "dfm_failed", "message", e.getMessage()));
        }
    }

    public record FixRequest(JsonNode spec, String prompt_et) {}

    /**
     * DFM + LLM kombo: jooksutab reegli-auditi, saadab leitud issue'd Claude'ile,
     * LLM tagastab strukturaalse remediation-plaani kus iga reegli-issue on
     * täidetud juba <em>kasutaja-sõbraliku, kontekstuaalse</em> selgitusega.
     * See on parem kui puhas rule-based, kuna LLM suudab öelda
     * "sinu 10kg koormuse juures on 2mm sein liiga õhuke ja siin on miks",
     * mitte lihtsalt "wall_thickness &lt; 2".
     */
    @PostMapping("/fix")
    public ResponseEntity<?> fix(@RequestBody FixRequest req) {
        if (req == null || req.spec() == null || !req.spec().hasNonNull("template")) {
            return ResponseEntity.badRequest().body(Map.of("error", "spec_required"));
        }
        try {
            JsonNode dfm = worker.dfmAnalyze(req.spec());
            // Kui pole ühtegi issue'd — return kohe ilma LLM kutseta (säästad raha)
            if (dfm.path("counts").path("critical").asInt(0) == 0 &&
                dfm.path("counts").path("warning").asInt(0) == 0 &&
                dfm.path("counts").path("info").asInt(0) == 0) {
                ObjectNode out = mapper.createObjectNode();
                out.set("dfm", dfm);
                out.put("verdict_et", "Rule-based audit leidis nullivigu — LLM-revision pole vajalik.");
                out.putArray("fixes");
                return ResponseEntity.ok(out);
            }
            // Kombineerime DFM raporti + LLM persona review'ga (struktuur + protsess),
            // millest sünteesime lühema fix-plaani.
            JsonNode review = claude.reviewAsPersona(
                    AgentPersona.PROCESS, req.prompt_et(), req.spec(), dfm, null);
            ObjectNode out = mapper.createObjectNode();
            out.set("dfm", dfm);
            out.set("review", review);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "ai_disabled",
                    "message", "Claude API võti pole seadistatud."));
        } catch (Exception e) {
            log.error("Fix ebaõnnestus", e);
            return ResponseEntity.status(502).body(Map.of("error", "fix_failed", "message", e.getMessage()));
        }
    }
}
