package ee.krerte.cad.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.krerte.cad.auth.DesignRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Design version history — every regeneration saves a snapshot. Users can browse history and
 * rollback to any version.
 */
@RestController
@RequestMapping("/api/designs/{designId}/versions")
public class VersionController {

    private final DesignVersionRepository versionRepo;
    private final DesignRepository designRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public VersionController(DesignVersionRepository versionRepo, DesignRepository designRepo) {
        this.versionRepo = versionRepo;
        this.designRepo = designRepo;
    }

    private Long uid() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** List all versions of a design. */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long designId) {
        var design = designRepo.findById(designId).orElse(null);
        if (design == null || !design.getUserId().equals(uid())) {
            return ResponseEntity.status(404).build();
        }

        List<Map<String, Object>> versions =
                versionRepo.findByDesignIdOrderByVersionDesc(designId).stream()
                        .map(
                                v -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("version", v.getVersion());
                                    m.put("params", safeJson(v.getParams()));
                                    m.put("summary_et", v.getSummaryEt());
                                    m.put("size_bytes", v.getSizeBytes());
                                    m.put("created_at", v.getCreatedAt().toString());
                                    return m;
                                })
                        .toList();
        return ResponseEntity.ok(versions);
    }

    /** Rollback: restore a specific version's params to the main design. */
    @PostMapping("/{version}/rollback")
    public ResponseEntity<?> rollback(@PathVariable Long designId, @PathVariable int version) {
        var design = designRepo.findById(designId).orElse(null);
        if (design == null || !design.getUserId().equals(uid())) {
            return ResponseEntity.status(404).build();
        }

        var targetVersion = versionRepo.findByDesignIdAndVersion(designId, version).orElse(null);
        if (targetVersion == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Versiooni ei leitud"));
        }

        // Save current state as a new version before rollback
        int nextVer = versionRepo.maxVersion(designId) + 1;
        DesignVersion snapshot = new DesignVersion();
        snapshot.setDesignId(designId);
        snapshot.setVersion(nextVer);
        snapshot.setParams(design.getParams());
        snapshot.setSummaryEt(design.getSummaryEt());
        snapshot.setStl(design.getStl());
        versionRepo.save(snapshot);

        // Restore the target version
        design.setParams(targetVersion.getParams());
        design.setSummaryEt(targetVersion.getSummaryEt());
        if (targetVersion.getStl() != null) {
            design.setStl(targetVersion.getStl());
        }
        designRepo.save(design);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Taastatud versioon " + version,
                        "params", safeJson(targetVersion.getParams()),
                        "current_version", nextVer + 1));
    }

    /** Download STL of a specific version. */
    @GetMapping("/{version}/stl")
    public ResponseEntity<?> versionStl(@PathVariable Long designId, @PathVariable int version) {
        var design = designRepo.findById(designId).orElse(null);
        if (design == null || !design.getUserId().equals(uid())) {
            return ResponseEntity.status(404).build();
        }

        var v = versionRepo.findByDesignIdAndVersion(designId, version).orElse(null);
        if (v == null || v.getStl() == null) {
            return ResponseEntity.status(404).body(Map.of("error", "STL puudub sellel versioonil"));
        }

        return ResponseEntity.ok()
                .header("Content-Type", "application/sla")
                .header("Content-Disposition", "attachment; filename=\"v" + version + ".stl\"")
                .body(v.getStl());
    }

    private JsonNode safeJson(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
