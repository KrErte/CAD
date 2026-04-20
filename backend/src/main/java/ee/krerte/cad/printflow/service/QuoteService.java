package ee.krerte.cad.printflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.krerte.cad.SlicerClient;
import ee.krerte.cad.auth.User;
import ee.krerte.cad.printflow.entity.*;
import ee.krerte.cad.printflow.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orkestraator: STL upload → DFM → slicer → hind → salvestus.
 *
 * Ainus "instant-quote" kood, mida kogu tööstus vajab.
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final QuoteRepository quoteRepo;
    private final QuoteLineRepository lineRepo;
    private final MaterialRepository materialRepo;
    private final DfmService dfmService;
    private final SlicerClient slicerClient;
    private final PricingService pricing;
    private final PrintJobRepository jobRepo;
    private final ObjectMapper om;

    public QuoteService(QuoteRepository q, QuoteLineRepository l, MaterialRepository m,
                        DfmService d, SlicerClient s, PricingService p,
                        PrintJobRepository j, ObjectMapper om) {
        this.quoteRepo = q;
        this.lineRepo = l;
        this.materialRepo = m;
        this.dfmService = d;
        this.slicerClient = s;
        this.pricing = p;
        this.jobRepo = j;
        this.om = om;
    }

    @Transactional
    public QuoteResult createFromUpload(
            Organization org,
            User createdBy,
            byte[] stl,
            String fileName,
            Long materialId,
            Integer quantity,
            Integer infillPct,
            String color,
            Long customerId,
            boolean rush
    ) {
        Material material = materialRepo.findByIdAndOrganizationId(materialId, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "materjal ei leitud"));

        // DFM analüüs (salvestatud raport)
        DfmReport dfm = dfmService.analyzeAndStore(org.getId(), stl, fileName, material);

        // Kui DFM BLOCK → ei ehita slicer quote'i, anname tagasi raporti
        if ("BLOCK".equals(dfm.getSeverity())) {
            log.info("DFM BLOCK quote, ei genereeri hinda: dfmId={}", dfm.getId());
            return QuoteResult.blocked(dfm);
        }

        // Slicer eelvaade (print-time, filament, hind-alus)
        Map<String, Object> slicer = sliceOrFallback(stl, material, infillPct);

        double printTimeSec = toDouble(slicer.get("print_time_sec"));
        double filamentG = toDouble(slicer.get("filament_g"));
        if (filamentG <= 0) {
            // fallback volumeg*density
            double volCm3 = dfm.getVolumeCm3() != null ? dfm.getVolumeCm3().doubleValue() : 10.0;
            double infill = infillPct != null ? infillPct / 100.0 : 0.20;
            filamentG = volCm3 * material.getDensityGcm3().doubleValue() * (0.35 + 0.65 * infill);
        }
        if (printTimeSec <= 0) {
            // fallback 3h/100g
            printTimeSec = filamentG * 3600.0 / 100.0 * 3.0;
        }

        BigDecimal rushMult = rush ? new BigDecimal("1.5") : BigDecimal.ONE;
        PricingService.LinePricing pricing = this.pricing.calculate(
                org, material, quantity != null ? quantity : 1,
                printTimeSec, filamentG, rushMult);

        // Loo / lisa Quote
        Quote quote = buildOrReuseQuote(org, createdBy, customerId);
        QuoteLine line = new QuoteLine();
        line.setQuoteId(quote.getId());
        line.setLineNo(1);
        line.setFileName(fileName);
        line.setStlBytes(stl);
        line.setQuantity(quantity != null ? quantity : 1);
        line.setMaterialId(material.getId());
        line.setInfillPct(infillPct != null ? infillPct : 20);
        line.setColor(color);
        line.setUnitPriceEur(pricing.unitPriceEur);
        line.setTotalEur(pricing.totalEur);
        line.setDfmReportId(dfm.getId());
        try {
            line.setSlicerResult(om.writeValueAsString(slicer));
        } catch (Exception e) { /* ignore — pole kriitiline */ }
        line = lineRepo.save(line);

        // Uuenda Quote summa
        quote.setSubtotalEur(pricing.totalEur);
        quote.setSetupFeeEur(pricing.setupFeeEur);
        quote.setMarginPct(pricing.marginPct);
        quote.setTotalEur(pricing.totalEur);
        if (rush) quote.setRushMultiplier(new BigDecimal("1.5"));
        quote.setValidUntil(Instant.now().plus(14, ChronoUnit.DAYS));
        if (quote.getPublicToken() == null) quote.setPublicToken(generateToken());
        quote = quoteRepo.save(quote);

        return QuoteResult.ok(quote, line, dfm, slicer, pricing);
    }

    private Quote buildOrReuseQuote(Organization org, User user, Long customerId) {
        Quote q = new Quote();
        q.setOrganizationId(org.getId());
        q.setQuoteNumber(nextQuoteNumber(org));
        q.setCustomerId(customerId);
        q.setCreatedByUserId(user != null ? user.getId() : null);
        q.setMarginPct(org.getDefaultMarginPct());
        q.setSetupFeeEur(org.getDefaultSetupFeeEur());
        q.setStatus("DRAFT");
        return quoteRepo.save(q);
    }

    private String nextQuoteNumber(Organization org) {
        long count = quoteRepo.countByOrganizationId(org.getId());
        return String.format("Q-%d-%04d", Instant.now().atZone(java.time.ZoneOffset.UTC).getYear(), count + 1);
    }

    private Map<String, Object> sliceOrFallback(byte[] stl, Material material, Integer infillPct) {
        Map<String, Object> out = new HashMap<>();
        if (!slicerClient.enabled()) {
            out.put("source", "heuristic");
            return out;
        }
        try {
            String preset = material.getSlicerPreset() != null
                    ? material.getSlicerPreset()
                    : (material.getFamily() != null ? material.getFamily().toLowerCase() : "pla") + "_default";
            String fill = infillPct != null ? infillPct + "%" : "20%";
            JsonNode res = slicerClient.slice(stl, preset, fill, "0.20");
            out.put("source", "slicer");
            if (res.has("print_time_sec")) out.put("print_time_sec", res.get("print_time_sec").asLong());
            if (res.has("print_time_human")) out.put("print_time_human", res.get("print_time_human").asText());
            if (res.has("filament_g")) out.put("filament_g", res.get("filament_g").asDouble());
            if (res.has("filament_cost_eur")) out.put("filament_cost_eur", res.get("filament_cost_eur").asDouble());
            if (res.has("filament_volume_cm3")) out.put("filament_volume_cm3", res.get("filament_volume_cm3").asDouble());
        } catch (Exception e) {
            log.warn("Slicer ei vasta → heuristiline fallback: {}", e.getMessage());
            out.put("source", "heuristic_slicer_failed");
        }
        return out;
    }

    private static double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private String generateToken() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /**
     * Nõustumine — klient kinnitab quote'i. Loob PrintJob-id queue'sse.
     */
    @Transactional
    public Quote accept(Long quoteId, Long orgId) {
        Quote q = quoteRepo.findByIdAndOrganizationId(quoteId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "quote ei leitud"));
        if (!"DRAFT".equals(q.getStatus()) && !"SENT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quote pole nõustutav staatuses " + q.getStatus());
        }
        q.setStatus("ACCEPTED");
        q.setAcceptedAt(Instant.now());
        q = quoteRepo.save(q);

        // Loo PrintJob-id
        for (QuoteLine line : lineRepo.findByQuoteIdOrderByLineNoAsc(q.getId())) {
            for (int i = 0; i < line.getQuantity(); i++) {
                PrintJob job = new PrintJob();
                job.setOrganizationId(orgId);
                job.setQuoteId(q.getId());
                job.setQuoteLineId(line.getId());
                job.setMaterialId(line.getMaterialId());
                job.setPriority(50);
                job.setJobName((line.getFileName() != null ? line.getFileName() : "part")
                        + " (" + (i + 1) + "/" + line.getQuantity() + ")");
                jobRepo.save(job);
            }
        }
        log.info("Quote {} aktsepteeritud, {} line'i → jobs", q.getQuoteNumber(), lineRepo.findByQuoteIdOrderByLineNoAsc(q.getId()).size());
        return q;
    }

    @Transactional(readOnly = true)
    public List<Quote> list(Long orgId) {
        return quoteRepo.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional(readOnly = true)
    public Quote get(Long id, Long orgId) {
        return quoteRepo.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "quote ei leitud"));
    }

    @Transactional(readOnly = true)
    public List<QuoteLine> linesOf(Long quoteId) {
        return lineRepo.findByQuoteIdOrderByLineNoAsc(quoteId);
    }

    // ── Result DTO ────────────────────────────────────────────────────

    public static class QuoteResult {
        public Quote quote;
        public QuoteLine line;
        public DfmReport dfm;
        public Map<String, Object> slicer;
        public PricingService.LinePricing pricing;
        public String message;
        public boolean blocked;

        public static QuoteResult ok(Quote q, QuoteLine l, DfmReport d, Map<String, Object> s, PricingService.LinePricing p) {
            QuoteResult r = new QuoteResult();
            r.quote = q; r.line = l; r.dfm = d; r.slicer = s; r.pricing = p;
            return r;
        }

        public static QuoteResult blocked(DfmReport d) {
            QuoteResult r = new QuoteResult();
            r.dfm = d;
            r.blocked = true;
            r.message = "DFM blokeeris quote loomise — vaata issue'id";
            return r;
        }
    }
}
