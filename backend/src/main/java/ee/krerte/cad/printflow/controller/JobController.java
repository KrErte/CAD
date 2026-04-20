package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.entity.PrintJob;
import ee.krerte.cad.printflow.repo.PrintJobRepository;
import ee.krerte.cad.printflow.service.OrganizationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printflow/jobs")
public class JobController {

    private final PrintJobRepository repo;
    private final OrganizationContext orgCtx;

    public JobController(PrintJobRepository r, OrganizationContext o) {
        this.repo = r;
        this.orgCtx = o;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(value = "status", required = false) String status) {
        Organization org = orgCtx.currentOrganization();
        List<PrintJob> all = status != null
                ? repo.findByOrganizationIdAndStatus(org.getId(), status)
                : repo.findByOrganizationIdOrderByQueuedAtDesc(org.getId());
        return all.stream().map(JobController::render).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        PrintJob j = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return render(j);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        PrintJob j = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("DONE".equals(j.getStatus()) || "CANCELLED".equals(j.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "juba lõpetatud");
        }
        j.setStatus("CANCELLED");
        j.setFinishedAt(Instant.now());
        j = repo.save(j);
        return render(j);
    }

    @PostMapping("/{id}/priority")
    public Map<String, Object> priority(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        PrintJob j = repo.findByIdAndOrganizationId(id, org.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Integer pri = body.get("priority");
        if (pri == null || pri < 0 || pri > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "priority 0..100");
        }
        j.setPriority(pri);
        j = repo.save(j);
        return render(j);
    }

    public static Map<String, Object> render(PrintJob j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("status", j.getStatus());
        m.put("priority", j.getPriority());
        m.put("job_name", j.getJobName());
        m.put("quote_id", j.getQuoteId());
        m.put("quote_line_id", j.getQuoteLineId());
        m.put("material_id", j.getMaterialId());
        m.put("printer_id", j.getPrinterId());
        m.put("progress_pct", j.getProgressPct());
        m.put("estimated_time_sec", j.getEstimatedTimeSec());
        m.put("estimated_filament_g", j.getEstimatedFilamentG());
        m.put("queued_at", j.getQueuedAt());
        m.put("started_at", j.getStartedAt());
        m.put("finished_at", j.getFinishedAt());
        m.put("failure_reason", j.getFailureReason());
        return m;
    }
}
