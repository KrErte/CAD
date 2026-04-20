package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.DfmReport;
import ee.krerte.cad.printflow.entity.Material;
import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.repo.MaterialRepository;
import ee.krerte.cad.printflow.service.DfmService;
import ee.krerte.cad.printflow.service.OrganizationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Map;

/** DFM endpoint — iseseisev (quote'ist sõltumatu) analüüs. */
@RestController
@RequestMapping("/api/printflow/dfm")
public class DfmController {

    private final DfmService dfm;
    private final MaterialRepository materials;
    private final OrganizationContext orgCtx;

    public DfmController(DfmService d, MaterialRepository m, OrganizationContext o) {
        this.dfm = d;
        this.materials = m;
        this.orgCtx = o;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestParam("stl") MultipartFile stl,
            @RequestParam(value = "material_id", required = false) Long materialId
    ) throws IOException {
        Organization org = orgCtx.currentOrganization();

        Material material = null;
        if (materialId != null) {
            material = materials.findByIdAndOrganizationId(materialId, org.getId()).orElse(null);
        }

        if (stl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STL puudub");
        }

        DfmReport r = dfm.analyzeAndStore(org.getId(), stl.getBytes(),
                stl.getOriginalFilename(), material);
        return ResponseEntity.ok(QuoteController.renderDfm(r));
    }
}
