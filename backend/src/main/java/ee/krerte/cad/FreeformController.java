package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.krerte.cad.auth.QuotaService;
import ee.krerte.cad.auth.User;
import ee.krerte.cad.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Freeform CadQuery sandbox — lubab Pro+ kasutajal kirjutada oma CadQuery koodi
 * ja see jookseb turvalises worker-sandbox'is (AST-whitelist, 15s timeout,
 * 512MB memory limit).
 *
 * <p>FREE plaanil on kuupõhine proovikvoot (vaikimisi 3 katset —
 * {@code app.quota.free-freeform-monthly}). Peale kvoota ammendumist
 * tagastame 403 {@code upgrade_required} — PRO+ on limiidita.
 * See on meie Pro-plaani peamine eristaja: 23 malli pole piisav → kirjuta ise.
 *
 * <p>Worker tagastab <code>{ok, error, error_kind, files:{stl,step}, elapsed_ms}</code>
 * ja me edastame selle frontendile muutmata kujul. STL/STEP on base64-kodeeritud
 * stringi­dena JSON-is, et front saaks kohe data-url-iga alla laadida.
 */
@RestController
@RequestMapping("/api/freeform")
public class FreeformController {

    private static final Logger log = LoggerFactory.getLogger(FreeformController.class);

    private final WorkerClient worker;
    private final UserRepository users;
    private final QuotaService quotas;
    private final ObjectMapper mapper = new ObjectMapper();

    public FreeformController(WorkerClient worker, UserRepository users, QuotaService quotas) {
        this.worker = worker;
        this.users = users;
        this.quotas = quotas;
    }

    public record FreeformRequest(String code) {}

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody FreeformRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "Code is empty",
                    "error_kind", "empty_code"));
        }
        if (req.code().length() > 20_000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "Code too long (max 20 000 chars)",
                    "error_kind", "too_long"));
        }

        // Plan check — ainult PRO+ saavad freeform kasutada
        Optional<User> uOpt = currentUser();
        if (uOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "ok", false,
                    "error", "Login required",
                    "error_kind", "unauthorized"));
        }
        User u = uOpt.get();
        // FREE plaanile anname väikse proovikvoota (vaikimisi 3/kuus), et
        // kasutaja saaks Pro-feature'it kogeda enne ostu. PRO+ on limiidita.
        // Kvoot ületatud → 403 upgrade_required. Muidu reserveerime enne
        // workerisse saatmist (optimistlik — kui worker kukub, kulub katse,
        // aga see on ok: vastuses tuleb error_kind ja kasutaja näeb probleemi).
        QuotaService.Status q = quotas.freeformStatus(u.getId());
        if (!q.allowed()) {
            String plan = u.getPlan() == null ? "FREE" : u.getPlan().name();
            return ResponseEntity.status(403).body(Map.of(
                    "ok", false,
                    "error", "Vaba plaani katsetuskvoot täis (" + q.used() + "/" + q.limit()
                            + " sel kuul). Uuenda Pro plaanile, et jätkata piiranguta.",
                    "error_kind", "upgrade_required",
                    "current_plan", plan,
                    "required_plan", "PRO",
                    "used", q.used(),
                    "limit", q.limit()));
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("code", req.code());
        body.put("user_id", u.getId());

        JsonNode result;
        try {
            result = worker.freeformGenerate(body);
        } catch (Exception e) {
            log.error("Worker freeform failed", e);
            return ResponseEntity.status(502).body(Map.of(
                    "ok", false,
                    "error", e.getMessage() == null ? "Worker error" : e.getMessage(),
                    "error_kind", "worker_unreachable"));
        }
        // Loeme edukad (ok=true) katsed kvoota alla — ebaõnnestunud
        // runtime/syntax-vigu ei taha kasutajalt kätte maksta.
        if (result != null && result.path("ok").asBoolean(false)) {
            quotas.recordFreeform(u.getId());
        }
        return ResponseEntity.ok(result);
    }

    private Optional<User> currentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (!(principal instanceof Long)) return Optional.empty();
            return users.findById((Long) principal);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
