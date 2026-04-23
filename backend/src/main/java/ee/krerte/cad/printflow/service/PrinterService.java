package ee.krerte.cad.printflow.service;

import ee.krerte.cad.printflow.adapter.AdapterStatus;
import ee.krerte.cad.printflow.adapter.MockPrinterAdapter;
import ee.krerte.cad.printflow.adapter.PrinterAdapter;
import ee.krerte.cad.printflow.adapter.PrinterAdapterFactory;
import ee.krerte.cad.printflow.entity.PrintJob;
import ee.krerte.cad.printflow.entity.Printer;
import ee.krerte.cad.printflow.entity.PrinterEvent;
import ee.krerte.cad.printflow.repo.PrintJobRepository;
import ee.krerte.cad.printflow.repo.PrinterEventRepository;
import ee.krerte.cad.printflow.repo.PrinterRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrinterService {

    private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

    private final PrinterRepository repo;
    private final PrinterEventRepository eventRepo;
    private final PrintJobRepository jobRepo;
    private final PrinterAdapterFactory adapterFactory;
    private final PrinterEventPublisher eventPub;
    private final MockPrinterAdapter mockAdapter;

    public PrinterService(
            PrinterRepository r,
            PrinterEventRepository er,
            PrintJobRepository jr,
            PrinterAdapterFactory f,
            PrinterEventPublisher ep,
            MockPrinterAdapter ma) {
        this.repo = r;
        this.eventRepo = er;
        this.jobRepo = jr;
        this.adapterFactory = f;
        this.eventPub = ep;
        this.mockAdapter = ma;
    }

    @Transactional(readOnly = true)
    public List<Printer> list(Long orgId) {
        return repo.findByOrganizationIdOrderByNameAsc(orgId);
    }

    @Transactional(readOnly = true)
    public Printer get(Long id, Long orgId) {
        return repo.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "printer ei leitud"));
    }

    @Transactional
    public Printer create(Long orgId, Printer input) {
        input.setOrganizationId(orgId);
        if (input.getStatus() == null) input.setStatus("OFFLINE");
        return repo.save(input);
    }

    @Transactional
    public Printer update(Long id, Long orgId, Printer patch) {
        Printer p = get(id, orgId);
        if (patch.getName() != null) p.setName(patch.getName());
        if (patch.getVendor() != null) p.setVendor(patch.getVendor());
        if (patch.getModel() != null) p.setModel(patch.getModel());
        if (patch.getBuildVolumeXmm() != null) p.setBuildVolumeXmm(patch.getBuildVolumeXmm());
        if (patch.getBuildVolumeYmm() != null) p.setBuildVolumeYmm(patch.getBuildVolumeYmm());
        if (patch.getBuildVolumeZmm() != null) p.setBuildVolumeZmm(patch.getBuildVolumeZmm());
        if (patch.getSupportedMaterialFamilies() != null)
            p.setSupportedMaterialFamilies(patch.getSupportedMaterialFamilies());
        if (patch.getAdapterType() != null) p.setAdapterType(patch.getAdapterType());
        if (patch.getAdapterUrl() != null) p.setAdapterUrl(patch.getAdapterUrl());
        if (patch.getHourlyRateEur() != null) p.setHourlyRateEur(patch.getHourlyRateEur());
        if (patch.getNotes() != null) p.setNotes(patch.getNotes());
        return repo.save(p);
    }

    @Transactional
    public void delete(Long id, Long orgId) {
        Printer p = get(id, orgId);
        repo.delete(p);
    }

    /**
     * Tõmba värske info printerilt ja salvesta event + SSE push. See kutsutakse JobScheduler-ist
     * (scheduled) + UI "refresh" nupu peale.
     */
    @Transactional
    public Printer heartbeat(Printer p) {
        PrinterAdapter adapter = adapterFactory.forPrinter(p);
        AdapterStatus st;
        try {
            st = adapter.refresh(p);
        } catch (Exception e) {
            log.warn(
                    "Adapter {} heartbeat ebaõnnestus printerile {}: {}",
                    adapter.supportsType(),
                    p.getName(),
                    e.getMessage());
            st = AdapterStatus.offline();
        }

        p.setStatus(st.status);
        p.setProgressPct(st.progressPct != null ? st.progressPct : 0);
        p.setBedTempC(st.bedTempC);
        p.setHotendTempC(st.hotendTempC);
        p.setLastHeartbeatAt(Instant.now());
        Printer saved = repo.save(p);

        // Jälgi, kas MOCK-adapter lõpetas töö
        if (mockAdapter.consumeCompletionFlag(p.getId())) {
            completeCurrentJob(saved, true, null);
        }

        PrinterEvent ev = new PrinterEvent();
        ev.setPrinterId(p.getId());
        ev.setEventType("HEARTBEAT");
        ev.setPayload(
                String.format(
                        "{\"status\":\"%s\",\"progress_pct\":%d,\"bed_c\":%s,\"hotend_c\":%s}",
                        st.status,
                        st.progressPct == null ? 0 : st.progressPct,
                        st.bedTempC,
                        st.hotendTempC));
        eventRepo.save(ev);

        eventPub.publish(
                p.getOrganizationId(),
                "printer",
                Map.of(
                        "printer_id", p.getId(),
                        "status", p.getStatus(),
                        "progress_pct", p.getProgressPct(),
                        "bed_c", p.getBedTempC(),
                        "hotend_c", p.getHotendTempC(),
                        "current_job_id", p.getCurrentJobId()));
        return saved;
    }

    @Transactional
    public void completeCurrentJob(Printer p, boolean success, String failureReason) {
        if (p.getCurrentJobId() == null) return;
        PrintJob job = jobRepo.findById(p.getCurrentJobId()).orElse(null);
        if (job == null) {
            p.setCurrentJobId(null);
            repo.save(p);
            return;
        }
        job.setStatus(success ? "DONE" : "FAILED");
        job.setProgressPct(success ? 100 : job.getProgressPct());
        job.setFinishedAt(Instant.now());
        if (!success) job.setFailureReason(failureReason != null ? failureReason : "unknown");
        jobRepo.save(job);

        p.setCurrentJobId(null);
        p.setProgressPct(0);
        repo.save(p);

        PrinterEvent ev = new PrinterEvent();
        ev.setPrinterId(p.getId());
        ev.setEventType(success ? "JOB_DONE" : "JOB_FAIL");
        ev.setPayload("{\"job_id\":" + job.getId() + "}");
        eventRepo.save(ev);

        eventPub.publish(
                p.getOrganizationId(),
                success ? "job.complete" : "job.fail",
                Map.of("job_id", job.getId(), "printer_id", p.getId()));
    }

    @Transactional
    public void pause(Printer p) {
        adapterFactory.forPrinter(p).pause(p);
        p.setStatus("PAUSED");
        repo.save(p);
    }

    @Transactional
    public void resume(Printer p) {
        adapterFactory.forPrinter(p).resume(p);
        p.setStatus("PRINTING");
        repo.save(p);
    }

    @Transactional
    public void cancel(Printer p) {
        adapterFactory.forPrinter(p).cancel(p);
        completeCurrentJob(p, false, "cancelled by operator");
        p.setStatus("IDLE");
        repo.save(p);
    }

    /** Saadab job gcode'i adapterile + märgib PrintJob'i ASSIGNED. */
    @Transactional
    public void dispatchJob(Printer p, PrintJob job) {
        PrinterAdapter adapter = adapterFactory.forPrinter(p);
        byte[] gcode = new byte[0]; // V1: mocked; V2 → genereerime slicer-sidecar'iga
        String adapterJobId;
        try {
            adapterJobId = adapter.dispatch(p, gcode, job.getJobName());
        } catch (Exception e) {
            log.warn("Dispatch ebaõnnestus: {}", e.getMessage());
            job.setStatus("QUEUED");
            job.setRetries(job.getRetries() + 1);
            jobRepo.save(job);
            return;
        }
        job.setStatus("PRINTING");
        job.setPrinterId(p.getId());
        job.setStartedAt(Instant.now());
        jobRepo.save(job);

        p.setCurrentJobId(job.getId());
        p.setStatus("PRINTING");
        p.setProgressPct(0);
        repo.save(p);

        PrinterEvent ev = new PrinterEvent();
        ev.setPrinterId(p.getId());
        ev.setEventType("JOB_START");
        ev.setPayload(
                "{\"job_id\":" + job.getId() + ",\"adapter_job_id\":\"" + adapterJobId + "\"}");
        eventRepo.save(ev);
    }
}
