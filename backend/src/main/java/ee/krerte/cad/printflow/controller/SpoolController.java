package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.FilamentSpool;
import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.repo.FilamentSpoolRepository;
import ee.krerte.cad.printflow.service.OrganizationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printflow/spools")
public class SpoolController {

    private final FilamentSpoolRepository repo;
    private final OrganizationContext orgCtx;

    public SpoolController(FilamentSpoolRepository r, OrganizationContext o) {
        this.repo = r;
        this.orgCtx = o;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Organization org = orgCtx.currentOrganization();
        return repo.findByOrganizationIdOrderByCreatedAtDesc(org.getId())
                .stream().map(SpoolController::render).toList();
    }

    @GetMapping("/low-stock")
    public List<Map<String, Object>> lowStock(@RequestParam(value = "threshold_g", defaultValue = "100") int thresholdG) {
        Organization org = orgCtx.currentOrganization();
        return repo.findLowStock(org.getId(), thresholdG).stream().map(SpoolController::render).toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody FilamentSpool input) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        input.setOrganizationId(org.getId());
        return render(repo.save(input));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody FilamentSpool patch) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        FilamentSpool s = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (patch.getColor() != null) s.setColor(patch.getColor());
        if (patch.getColorHex() != null) s.setColorHex(patch.getColorHex());
        if (patch.getMassRemainingG() != null) s.setMassRemainingG(patch.getMassRemainingG());
        if (patch.getAssignedPrinterId() != null) s.setAssignedPrinterId(patch.getAssignedPrinterId());
        if (patch.getStatus() != null) s.setStatus(patch.getStatus());
        if (patch.getVendor() != null) s.setVendor(patch.getVendor());
        if (patch.getLotCode() != null) s.setLotCode(patch.getLotCode());

        // auto-update status based on mass
        if (s.getMassRemainingG() != null && s.getMassInitialG() != null) {
            if (s.getMassRemainingG() <= 0) s.setStatus("EMPTY");
            else if (s.getMassRemainingG() < s.getMassInitialG() * 0.05) s.setStatus("PARTIAL");
        }

        s.setUpdatedAt(Instant.now());
        return render(repo.save(s));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        FilamentSpool s = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        s.setStatus("DISPOSED");
        repo.save(s);
        return Map.of("disposed", true);
    }

    public static Map<String, Object> render(FilamentSpool s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("material_id", s.getMaterialId());
        m.put("color", s.getColor());
        m.put("color_hex", s.getColorHex());
        m.put("mass_initial_g", s.getMassInitialG());
        m.put("mass_remaining_g", s.getMassRemainingG());
        m.put("pct_remaining", s.getMassInitialG() != null && s.getMassInitialG() > 0
                ? Math.round((double) s.getMassRemainingG() / s.getMassInitialG() * 100) : 0);
        m.put("status", s.getStatus());
        m.put("serial_barcode", s.getSerialBarcode());
        m.put("vendor", s.getVendor());
        m.put("lot_code", s.getLotCode());
        m.put("assigned_printer_id", s.getAssignedPrinterId());
        m.put("purchased_at", s.getPurchasedAt());
        m.put("expires_at", s.getExpiresAt());
        return m;
    }
}
