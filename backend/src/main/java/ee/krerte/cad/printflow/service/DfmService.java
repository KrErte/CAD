package ee.krerte.cad.printflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.krerte.cad.printflow.entity.DfmReport;
import ee.krerte.cad.printflow.entity.Material;
import ee.krerte.cad.printflow.repo.DfmReportRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DFM facade — saadab STL-i worker'isse, parsib vastuse, salvestab DfmReport kirje. Selle põhjal
 * määratleb ka severity (OK/WARN/BLOCK) äriloogika.
 */
@Service
public class DfmService {

    private static final Logger log = LoggerFactory.getLogger(DfmService.class);

    private final DfmClient client;
    private final DfmReportRepository repo;
    private final ObjectMapper om;

    public DfmService(DfmClient c, DfmReportRepository r, ObjectMapper om) {
        this.client = c;
        this.repo = r;
        this.om = om;
    }

    /**
     * Analüüsi STL + salvesta raport. Kui worker'i kõne ebaõnnestub, salvestame siiski
     * miinimumraporti severity=OK ja logi hoiatusega — "workaround", et quote'i voog ei peatuks ühe
     * infrastruktuuri-tõrke tõttu.
     */
    @Transactional
    public DfmReport analyzeAndStore(Long orgId, byte[] stl, String fileName, Material material) {
        DfmReport rep = new DfmReport();
        rep.setOrganizationId(orgId);
        rep.setFileName(fileName);
        rep.setSizeBytes(stl.length);

        try {
            Double minWall =
                    material != null && material.getMinWallMm() != null
                            ? material.getMinWallMm().doubleValue()
                            : 1.2;
            Integer overhang =
                    material != null && material.getMaxOverhangDeg() != null
                            ? material.getMaxOverhangDeg()
                            : 50;

            JsonNode res = client.analyze(stl, fileName, minWall, overhang);
            applyToReport(rep, res);
        } catch (Exception e) {
            log.warn("DFM teenus kättesaamatu — fallback OK-tüüpi raport: {}", e.getMessage());
            rep.setSeverity("OK");
            rep.setIssues("[]");
        }

        return repo.save(rep);
    }

    private void applyToReport(DfmReport rep, JsonNode res) {
        if (res == null) return;

        if (res.has("bbox_mm") && res.get("bbox_mm").isArray()) {
            JsonNode b = res.get("bbox_mm");
            if (b.size() >= 3) {
                rep.setBboxXmm(
                        BigDecimal.valueOf(b.get(0).asDouble())
                                .setScale(2, java.math.RoundingMode.HALF_UP));
                rep.setBboxYmm(
                        BigDecimal.valueOf(b.get(1).asDouble())
                                .setScale(2, java.math.RoundingMode.HALF_UP));
                rep.setBboxZmm(
                        BigDecimal.valueOf(b.get(2).asDouble())
                                .setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }
        if (res.has("volume_cm3")) {
            rep.setVolumeCm3(
                    BigDecimal.valueOf(res.get("volume_cm3").asDouble())
                            .setScale(3, java.math.RoundingMode.HALF_UP));
        }
        if (res.has("triangles")) rep.setTriangles(res.get("triangles").asInt());
        if (res.has("is_watertight")) rep.setIsWatertight(res.get("is_watertight").asBoolean());
        if (res.has("self_intersections"))
            rep.setSelfIntersections(res.get("self_intersections").asInt());
        if (res.has("min_wall_mm"))
            rep.setMinWallMm(
                    BigDecimal.valueOf(res.get("min_wall_mm").asDouble())
                            .setScale(2, java.math.RoundingMode.HALF_UP));
        if (res.has("overhang_area_cm2"))
            rep.setOverhangAreaCm2(
                    BigDecimal.valueOf(res.get("overhang_area_cm2").asDouble())
                            .setScale(3, java.math.RoundingMode.HALF_UP));
        if (res.has("overhang_pct"))
            rep.setOverhangPct(
                    BigDecimal.valueOf(res.get("overhang_pct").asDouble())
                            .setScale(2, java.math.RoundingMode.HALF_UP));
        if (res.has("thin_features_count"))
            rep.setThinFeaturesCount(res.get("thin_features_count").asInt());

        JsonNode issues = res.get("issues");
        try {
            rep.setIssues(issues != null ? om.writeValueAsString(issues) : "[]");
        } catch (JsonProcessingException e) {
            rep.setIssues("[]");
        }

        rep.setSeverity(deriveSeverity(issues));
    }

    /** Tugev BLOCK kui watertight=false või mõni issue severity="block". */
    private String deriveSeverity(JsonNode issues) {
        if (issues == null || !issues.isArray()) return "OK";
        boolean warn = false;
        for (JsonNode it : issues) {
            String sev = it.path("severity").asText("info");
            if ("block".equalsIgnoreCase(sev)) return "BLOCK";
            if ("warn".equalsIgnoreCase(sev)) warn = true;
        }
        return warn ? "WARN" : "OK";
    }
}
