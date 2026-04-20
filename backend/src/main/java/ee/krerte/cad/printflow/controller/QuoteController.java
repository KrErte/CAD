package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.*;
import ee.krerte.cad.printflow.service.OrganizationContext;
import ee.krerte.cad.printflow.service.PricingService;
import ee.krerte.cad.printflow.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Instant Quote Engine — sisuke REST API.
 *
 * POST /api/printflow/quotes (multipart)
 *   stl, file_name, material_id, quantity, infill_pct, color, customer_id, rush
 * GET  /api/printflow/quotes
 * GET  /api/printflow/quotes/{id}
 * POST /api/printflow/quotes/{id}/accept
 * GET  /api/printflow/quotes/public/{token}   — publik "accept-link" kliendile
 */
@RestController
@RequestMapping("/api/printflow/quotes")
public class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final QuoteService quoteService;
    private final OrganizationContext orgCtx;

    public QuoteController(QuoteService q, OrganizationContext o) {
        this.quoteService = q;
        this.orgCtx = o;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createQuote(
            @RequestParam("stl") MultipartFile stl,
            @RequestParam(value = "material_id") Long materialId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
            @RequestParam(value = "infill_pct", defaultValue = "20") Integer infillPct,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "customer_id", required = false) Long customerId,
            @RequestParam(value = "rush", defaultValue = "false") boolean rush
    ) throws IOException {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");

        if (stl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STL puudub");
        }
        if (stl.getSize() > 50 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "STL üle 50 MB");
        }

        byte[] bytes = stl.getBytes();
        String fileName = stl.getOriginalFilename() != null ? stl.getOriginalFilename() : "part.stl";

        QuoteService.QuoteResult r = quoteService.createFromUpload(
                org, orgCtx.currentUser(),
                bytes, fileName, materialId, quantity, infillPct, color, customerId, rush);

        return ResponseEntity.ok(renderResult(r));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Organization org = orgCtx.currentOrganization();
        return quoteService.list(org.getId()).stream()
                .map(QuoteController::renderQuote)
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        Quote q = quoteService.get(id, org.getId());
        return renderQuoteFull(q, quoteService.linesOf(q.getId()));
    }

    @PostMapping("/{id}/accept")
    public Map<String, Object> accept(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        Quote q = quoteService.accept(id, org.getId());
        return Map.of("id", q.getId(), "status", q.getStatus(), "accepted_at", q.getAcceptedAt());
    }

    // ── DTO render ────────────────────────────────────────────────────

    public static Map<String, Object> renderQuote(Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("quote_number", q.getQuoteNumber());
        m.put("status", q.getStatus());
        m.put("customer_id", q.getCustomerId());
        m.put("total_eur", q.getTotalEur());
        m.put("created_at", q.getCreatedAt());
        m.put("valid_until", q.getValidUntil());
        m.put("public_token", q.getPublicToken());
        return m;
    }

    public static Map<String, Object> renderQuoteFull(Quote q, List<QuoteLine> lines) {
        Map<String, Object> m = renderQuote(q);
        List<Map<String, Object>> ls = new ArrayList<>();
        for (QuoteLine l : lines) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("id", l.getId());
            lm.put("line_no", l.getLineNo());
            lm.put("file_name", l.getFileName());
            lm.put("quantity", l.getQuantity());
            lm.put("material_id", l.getMaterialId());
            lm.put("unit_price_eur", l.getUnitPriceEur());
            lm.put("total_eur", l.getTotalEur());
            lm.put("dfm_report_id", l.getDfmReportId());
            lm.put("infill_pct", l.getInfillPct());
            lm.put("color", l.getColor());
            ls.add(lm);
        }
        m.put("lines", ls);
        return m;
    }

    private Map<String, Object> renderResult(QuoteService.QuoteResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (r.blocked) {
            out.put("blocked", true);
            out.put("message", r.message);
            out.put("dfm", renderDfm(r.dfm));
            return out;
        }
        out.put("blocked", false);
        out.put("quote", renderQuote(r.quote));
        out.put("line", Map.of(
                "id", r.line.getId(),
                "unit_price_eur", r.line.getUnitPriceEur(),
                "total_eur", r.line.getTotalEur()
        ));
        out.put("dfm", renderDfm(r.dfm));
        out.put("slicer", r.slicer);
        if (r.pricing != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("filament_cost_eur", r.pricing.filamentCostEur);
            p.put("time_cost_eur", r.pricing.timeCostEur);
            p.put("setup_fee_eur", r.pricing.setupFeeEur);
            p.put("margin_pct", r.pricing.marginPct);
            p.put("volume_discount_pct", r.pricing.volumeDiscountPct);
            p.put("unit_price_eur", r.pricing.unitPriceEur);
            p.put("total_eur", r.pricing.totalEur);
            out.put("pricing", p);
        }
        return out;
    }

    public static Map<String, Object> renderDfm(DfmReport d) {
        if (d == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("severity", d.getSeverity());
        m.put("is_watertight", d.getIsWatertight());
        m.put("triangles", d.getTriangles());
        m.put("volume_cm3", d.getVolumeCm3());
        m.put("bbox_mm", List.of(
                d.getBboxXmm() != null ? d.getBboxXmm() : BigDecimal.ZERO,
                d.getBboxYmm() != null ? d.getBboxYmm() : BigDecimal.ZERO,
                d.getBboxZmm() != null ? d.getBboxZmm() : BigDecimal.ZERO
        ));
        m.put("min_wall_mm", d.getMinWallMm());
        m.put("overhang_pct", d.getOverhangPct());
        m.put("thin_features_count", d.getThinFeaturesCount());
        m.put("issues_json", d.getIssues());
        m.put("created_at", d.getCreatedAt());
        return m;
    }
}
