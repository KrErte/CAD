package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.entity.WebhookSubscription;
import ee.krerte.cad.printflow.repo.WebhookSubscriptionRepository;
import ee.krerte.cad.printflow.service.OrganizationContext;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Webhook subscriptions — ERP / Slack / custom integratsioonid saavad kuulata quote.accepted,
 * job.finished, printer.offline jne sündmusi.
 */
@RestController
@RequestMapping("/api/printflow/webhooks")
public class WebhookController {

    private final WebhookSubscriptionRepository repo;
    private final OrganizationContext orgCtx;
    private final SecureRandom rng = new SecureRandom();

    public WebhookController(WebhookSubscriptionRepository r, OrganizationContext o) {
        this.repo = r;
        this.orgCtx = o;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Organization org = orgCtx.currentOrganization();
        return repo.findByOrganizationId(org.getId()).stream()
                .map(WebhookController::render)
                .toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody WebhookSubscription input) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        if (input.getTargetUrl() == null || input.getTargetUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_url puudub");
        }
        if (input.getEventTypes() == null || input.getEventTypes().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "event_types puudub (CSV, nt 'job.complete,quote.accepted')");
        }
        input.setOrganizationId(org.getId());
        if (input.getSecret() == null || input.getSecret().isBlank()) {
            byte[] b = new byte[32];
            rng.nextBytes(b);
            input.setSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(b));
        }
        if (input.getActive() == null) input.setActive(true);
        return renderWithSecret(repo.save(input));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable Long id, @RequestBody WebhookSubscription patch) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        WebhookSubscription w =
                repo.findByIdAndOrganizationId(id, org.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (patch.getTargetUrl() != null) w.setTargetUrl(patch.getTargetUrl());
        if (patch.getEventTypes() != null) w.setEventTypes(patch.getEventTypes());
        if (patch.getActive() != null) w.setActive(patch.getActive());
        return render(repo.save(w));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        WebhookSubscription w =
                repo.findByIdAndOrganizationId(id, org.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        repo.delete(w);
        return Map.of("deleted", true);
    }

    public static Map<String, Object> render(WebhookSubscription w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("event_types", w.getEventTypes());
        m.put("target_url", w.getTargetUrl());
        m.put("active", w.getActive());
        m.put("last_fired_at", w.getLastFiredAt());
        m.put("last_status_code", w.getLastStatusCode());
        m.put("created_at", w.getCreatedAt());
        return m;
    }

    public static Map<String, Object> renderWithSecret(WebhookSubscription w) {
        Map<String, Object> m = render(w);
        m.put("secret", w.getSecret());
        return m;
    }
}
