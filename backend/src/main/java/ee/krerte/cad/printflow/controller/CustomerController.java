package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Customer;
import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.repo.CustomerRepository;
import ee.krerte.cad.printflow.repo.QuoteRepository;
import ee.krerte.cad.printflow.service.OrganizationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printflow/customers")
public class CustomerController {

    private final CustomerRepository repo;
    private final QuoteRepository quoteRepo;
    private final OrganizationContext orgCtx;

    public CustomerController(CustomerRepository r, QuoteRepository q, OrganizationContext o) {
        this.repo = r;
        this.quoteRepo = q;
        this.orgCtx = o;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Organization org = orgCtx.currentOrganization();
        return repo.findByOrganizationIdOrderByCreatedAtDesc(org.getId())
                .stream().map(CustomerController::render).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        Customer c = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return render(c);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Customer input) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        input.setOrganizationId(org.getId());
        input.setUpdatedAt(Instant.now());
        return render(repo.save(input));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Customer patch) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        Customer c = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (patch.getKind() != null) c.setKind(patch.getKind());
        if (patch.getName() != null) c.setName(patch.getName());
        if (patch.getEmail() != null) c.setEmail(patch.getEmail());
        if (patch.getPhone() != null) c.setPhone(patch.getPhone());
        if (patch.getVatId() != null) c.setVatId(patch.getVatId());
        if (patch.getBillingAddress() != null) c.setBillingAddress(patch.getBillingAddress());
        if (patch.getShippingAddress() != null) c.setShippingAddress(patch.getShippingAddress());
        if (patch.getNotes() != null) c.setNotes(patch.getNotes());
        if (patch.getDefaultMarginPct() != null) c.setDefaultMarginPct(patch.getDefaultMarginPct());
        c.setUpdatedAt(Instant.now());
        return render(repo.save(c));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        Customer c = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        repo.delete(c);
        return Map.of("deleted", true);
    }

    @GetMapping("/{id}/quotes")
    public List<Map<String, Object>> quotesOf(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        // list all quotes for org, filter by customer
        return quoteRepo.findByOrganizationIdOrderByCreatedAtDesc(org.getId()).stream()
                .filter(q -> id.equals(q.getCustomerId()))
                .map(QuoteController::renderQuote)
                .toList();
    }

    public static Map<String, Object> render(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("kind", c.getKind());
        m.put("name", c.getName());
        m.put("email", c.getEmail());
        m.put("phone", c.getPhone());
        m.put("vat_id", c.getVatId());
        m.put("billing_address", c.getBillingAddress());
        m.put("shipping_address", c.getShippingAddress());
        m.put("default_margin_pct", c.getDefaultMarginPct());
        m.put("created_at", c.getCreatedAt());
        return m;
    }
}
