package ee.krerte.cad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * <p>Ainult PRO ja TEAM plaanidel — Free ja Maker näevad 403 "upgrade required".
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
    private final ObjectMapper mapper = new ObjectMapper();

    public FreeformController(WorkerClient worker, UserRepository users) {
        this.worker = worker;
        this.users = users;
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
        // User.Plan on enum (FREE, PRO). .name() annab String'i.
        // Kui tulevikus lisanduvad TEAM/ENTERPRISE, laieneb kontroll automaatselt.
        String plan = u.getPlan() == null ? "FREE" : u.getPlan().name();
        if (!plan.equals("PRO") && !plan.equals("BUSINESS") && !plan.equals("TEAM") && !plan.equals("ENTERPRISE")) {
            return ResponseEntity.status(403).body(Map.of(
                    "ok", false,
                    "error", "Freeform sandbox on saadaval ainult Pro ja Team plaanidel. Uuenda plaani, et jätkata.",
                    "error_kind", "upgrade_required",
                    "current_plan", plan,
                    "required_plan", "PRO"));
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
