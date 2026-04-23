package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.entity.Rfq;
import ee.krerte.cad.printflow.repo.OrganizationRepository;
import ee.krerte.cad.printflow.repo.RfqRepository;
import ee.krerte.cad.printflow.service.OrganizationContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Request-For-Quote voog — klient ise täidab vormi (public POST), admin näeb RFQ liste ja saab
 * muuta staatust.
 */
@RestController
@RequestMapping("/api/printflow/rfq")
public class RfqController {

    private final RfqRepository repo;
    private final OrganizationRepository orgRepo;
    private final OrganizationContext orgCtx;

    public RfqController(RfqRepository r, OrganizationRepository o, OrganizationContext oc) {
        this.repo = r;
        this.orgRepo = o;
        this.orgCtx = oc;
    }

    /** PUBLIC endpoint — klient täidab vormi. Org slug URL-is. */
    @PostMapping("/public/{slug}")
    public Map<String, Object> submit(@PathVariable String slug, @RequestBody Rfq input) {
        Organization org =
                orgRepo.findBySlug(slug)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "org ei leitud"));
        if (input.getContactEmail() == null || input.getContactEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contact_email puudub");
        }
        if (input.getContactName() == null || input.getContactName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contact_name puudub");
        }
        input.setOrganizationId(org.getId());
        input.setStatus("NEW");
        input.setUpdatedAt(Instant.now());
        Rfq saved = repo.save(input);
        return Map.of(
                "id",
                saved.getId(),
                "status",
                saved.getStatus(),
                "message_et",
                "Tänan! Võtame teiega ühendust 24h jooksul.");
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Organization org = orgCtx.currentOrganization();
        return repo.findByOrganizationIdOrderByCreatedAtDesc(org.getId()).stream()
                .map(RfqController::render)
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        Rfq r =
                repo.findByIdAndOrganizationId(id, org.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return render(r);
    }

    @PostMapping("/{id}/status")
    public Map<String, Object> status(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        Rfq r =
                repo.findByIdAndOrganizationId(id, org.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String next = body.getOrDefault("status", "IN_REVIEW");
        r.setStatus(next);
        r.setUpdatedAt(Instant.now());
        return render(repo.save(r));
    }

    public static Map<String, Object> render(Rfq r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("contact_name", r.getContactName());
        m.put("contact_email", r.getContactEmail());
        m.put("contact_phone", r.getContactPhone());
        m.put("description", r.getDescription());
        m.put("quantity_hint", r.getQuantityHint());
        m.put("material_hint", r.getMaterialHint());
        m.put("deadline", r.getDeadline());
        m.put("status", r.getStatus());
        m.put("assigned_to_user_id", r.getAssignedToUserId());
        m.put("quote_id", r.getQuoteId());
        m.put("created_at", r.getCreatedAt());
        return m;
    }
}
