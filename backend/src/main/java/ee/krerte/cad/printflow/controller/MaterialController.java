package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Material;
import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.repo.MaterialRepository;
import ee.krerte.cad.printflow.service.OrganizationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printflow/materials")
public class MaterialController {

    private final MaterialRepository repo;
    private final OrganizationContext orgCtx;

    public MaterialController(MaterialRepository r, OrganizationContext o) {
        this.repo = r;
        this.orgCtx = o;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(value = "all", defaultValue = "false") boolean all) {
        Organization org = orgCtx.currentOrganization();
        List<Material> ms = all
                ? repo.findByOrganizationIdOrderByFamilyAscNameAsc(org.getId())
                : repo.findByOrganizationIdAndActiveOrderByFamilyAscNameAsc(org.getId(), true);
        return ms.stream().map(MaterialController::render).toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Material input) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        input.setOrganizationId(org.getId());
        return render(repo.save(input));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Material patch) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        Material m = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (patch.getName() != null) m.setName(patch.getName());
        if (patch.getFamily() != null) m.setFamily(patch.getFamily());
        if (patch.getPricePerKgEur() != null) m.setPricePerKgEur(patch.getPricePerKgEur());
        if (patch.getDensityGcm3() != null) m.setDensityGcm3(patch.getDensityGcm3());
        if (patch.getSlicerPreset() != null) m.setSlicerPreset(patch.getSlicerPreset());
        if (patch.getMinWallMm() != null) m.setMinWallMm(patch.getMinWallMm());
        if (patch.getMaxOverhangDeg() != null) m.setMaxOverhangDeg(patch.getMaxOverhangDeg());
        if (patch.getSetupFeeEur() != null) m.setSetupFeeEur(patch.getSetupFeeEur());
        if (patch.getActive() != null) m.setActive(patch.getActive());
        return render(repo.save(m));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        Material m = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        m.setActive(false);
        repo.save(m);
        return Map.of("deleted", true);
    }

    public static Map<String, Object> render(Material m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("name", m.getName());
        r.put("family", m.getFamily());
        r.put("price_per_kg_eur", m.getPricePerKgEur());
        r.put("density_g_cm3", m.getDensityGcm3());
        r.put("slicer_preset", m.getSlicerPreset());
        r.put("min_wall_mm", m.getMinWallMm());
        r.put("max_overhang_deg", m.getMaxOverhangDeg());
        r.put("setup_fee_eur", m.getSetupFeeEur());
        r.put("active", m.getActive());
        return r;
    }
}
