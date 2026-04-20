package ee.krerte.cad.printflow.service;

import ee.krerte.cad.printflow.entity.PrintJob;
import ee.krerte.cad.printflow.entity.Quote;
import ee.krerte.cad.printflow.repo.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KPI-d: revenue, active jobs, success rate, printer OEE, top materials.
 */
@Service
public class AnalyticsService {

    private final QuoteRepository quoteRepo;
    private final PrintJobRepository jobRepo;
    private final PrinterRepository printerRepo;
    private final MaterialRepository materialRepo;

    public AnalyticsService(QuoteRepository q, PrintJobRepository j, PrinterRepository p, MaterialRepository m) {
        this.quoteRepo = q;
        this.jobRepo = j;
        this.printerRepo = p;
        this.materialRepo = m;
    }

    public Map<String, Object> kpi(Long orgId) {
        Instant since30 = Instant.now().minus(30, ChronoUnit.DAYS);
        Map<String, Object> r = new LinkedHashMap<>();

        // Revenue = sum of ACCEPTED quote totals
        BigDecimal revenue = quoteRepo.findByOrganizationIdAndStatus(orgId, "ACCEPTED").stream()
                .map(Quote::getTotalEur)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        r.put("revenue_eur", revenue);

        r.put("quotes_total", quoteRepo.countByOrganizationId(orgId));
        r.put("quotes_draft", quoteRepo.countByOrganizationIdAndStatus(orgId, "DRAFT"));
        r.put("quotes_accepted", quoteRepo.countByOrganizationIdAndStatus(orgId, "ACCEPTED"));

        List<PrintJob> all = jobRepo.findByOrganizationIdOrderByQueuedAtDesc(orgId);
        long done = all.stream().filter(j -> "DONE".equals(j.getStatus())).count();
        long failed = all.stream().filter(j -> "FAILED".equals(j.getStatus())).count();
        long queued = all.stream().filter(j -> "QUEUED".equals(j.getStatus())).count();
        long printing = all.stream().filter(j -> "PRINTING".equals(j.getStatus())).count();

        r.put("jobs_done_total", done);
        r.put("jobs_failed_total", failed);
        r.put("jobs_queued", queued);
        r.put("jobs_printing", printing);

        double success = (done + failed) == 0 ? 100.0 : 100.0 * done / (done + failed);
        r.put("success_rate_pct", BigDecimal.valueOf(success).setScale(1, RoundingMode.HALF_UP));

        long printersIdle = printerRepo.countByOrganizationIdAndStatus(orgId, "IDLE");
        long printersPrinting = printerRepo.countByOrganizationIdAndStatus(orgId, "PRINTING");
        long printersOffline = printerRepo.countByOrganizationIdAndStatus(orgId, "OFFLINE");
        r.put("printers_total", printersIdle + printersPrinting + printersOffline);
        r.put("printers_idle", printersIdle);
        r.put("printers_printing", printersPrinting);
        r.put("printers_offline", printersOffline);

        double oee = (printersIdle + printersPrinting) == 0 ? 0
                : 100.0 * printersPrinting / (printersIdle + printersPrinting + printersOffline);
        r.put("oee_pct", BigDecimal.valueOf(oee).setScale(1, RoundingMode.HALF_UP));
        r.put("calculated_at", Instant.now());
        return r;
    }

    public List<Map<String, Object>> topMaterials(Long orgId) {
        // group usage by material over last 90 days
        List<PrintJob> recent = jobRepo.findByOrganizationIdOrderByQueuedAtDesc(orgId);
        Map<Long, Long> counts = recent.stream()
                .filter(j -> j.getMaterialId() != null)
                .collect(Collectors.groupingBy(PrintJob::getMaterialId, Collectors.counting()));

        List<Map<String, Object>> out = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("material_id", e.getKey());
                    row.put("job_count", e.getValue());
                    materialRepo.findById(e.getKey()).ifPresent(m -> {
                        row.put("name", m.getName());
                        row.put("family", m.getFamily());
                    });
                    out.add(row);
                });
        return out;
    }

    public List<Map<String, Object>> revenueByDay(Long orgId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Map<String, BigDecimal> byDay = new TreeMap<>();
        for (Quote q : quoteRepo.findByOrganizationIdAndStatus(orgId, "ACCEPTED")) {
            if (q.getAcceptedAt() == null) continue;
            if (q.getAcceptedAt().isBefore(since)) continue;
            String d = q.getAcceptedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            byDay.merge(d, q.getTotalEur() != null ? q.getTotalEur() : BigDecimal.ZERO, BigDecimal::add);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byDay.forEach((day, eur) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", day);
            r.put("revenue_eur", eur);
            out.add(r);
        });
        return out;
    }
}
