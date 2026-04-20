package ee.krerte.cad.printflow.service;

import ee.krerte.cad.printflow.entity.Material;
import ee.krerte.cad.printflow.entity.PrintJob;
import ee.krerte.cad.printflow.entity.Printer;
import ee.krerte.cad.printflow.repo.MaterialRepository;
import ee.krerte.cad.printflow.repo.PrintJobRepository;
import ee.krerte.cad.printflow.repo.PrinterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Peamine scheduler-luup. Kord 15 sekundis:
 *   1) iga printeri peal heartbeat();
 *   2) iga IDLE printeri peal — leia queue'st sobiv job, dispatcher'da.
 *
 * Match'iv loogika:
 *   - material.family peab olema printeri supported_material_families CSV'is
 *   - priority desc, queuedAt asc
 */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final PrinterRepository printerRepo;
    private final PrintJobRepository jobRepo;
    private final MaterialRepository materialRepo;
    private final PrinterService printerService;

    public JobScheduler(PrinterRepository p, PrintJobRepository j, MaterialRepository m, PrinterService ps) {
        this.printerRepo = p;
        this.jobRepo = j;
        this.materialRepo = m;
        this.printerService = ps;
    }

    @Scheduled(fixedDelayString = "${app.printflow.scheduler.heartbeat-ms:15000}",
               initialDelayString = "${app.printflow.scheduler.initial-ms:5000}")
    public void tick() {
        try {
            List<Printer> all = printerRepo.findAll();
            for (Printer p : all) {
                try {
                    printerService.heartbeat(p);
                } catch (Exception e) {
                    log.warn("Heartbeat printer={} ebaõnnestus: {}", p.getId(), e.getMessage());
                }
            }
            assignPending(all);
        } catch (Exception e) {
            log.warn("Scheduler tick ebaõnnestus: {}", e.getMessage());
        }
    }

    @Transactional
    public void assignPending(List<Printer> printers) {
        for (Printer p : printers) {
            if (!"IDLE".equals(p.getStatus())) continue;
            if (p.getCurrentJobId() != null) continue;

            PrintJob next = pickNextForPrinter(p);
            if (next != null) {
                log.info("Assignin job {} → printer {}", next.getId(), p.getName());
                printerService.dispatchJob(p, next);
            }
        }
    }

    /**
     * Leiame printerile sobiva queue-järgse töö (material-match + priority).
     */
    private PrintJob pickNextForPrinter(Printer printer) {
        Set<String> supported = parseSupportedFamilies(printer.getSupportedMaterialFamilies());
        List<PrintJob> queued = jobRepo.findByOrganizationIdAndStatusOrderByPriorityDescQueuedAtAsc(
                printer.getOrganizationId(), "QUEUED");
        for (PrintJob job : queued) {
            if (job.getMaterialId() == null) return job;  // ilma-materjali-töö → anna ükskõik mis printerile
            Material m = materialRepo.findById(job.getMaterialId()).orElse(null);
            if (m == null) continue;
            if (supported.contains(m.getFamily())) return job;
        }
        return null;
    }

    private static Set<String> parseSupportedFamilies(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
