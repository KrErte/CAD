package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.entity.Printer;
import ee.krerte.cad.printflow.service.OrganizationContext;
import ee.krerte.cad.printflow.service.PrinterEventPublisher;
import ee.krerte.cad.printflow.service.PrinterService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/printflow/printers")
public class PrinterController {

    private final PrinterService service;
    private final OrganizationContext orgCtx;
    private final PrinterEventPublisher events;

    public PrinterController(PrinterService s, OrganizationContext o, PrinterEventPublisher e) {
        this.service = s;
        this.orgCtx = o;
        this.events = e;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Organization org = orgCtx.currentOrganization();
        return service.list(org.getId()).stream().map(PrinterController::render).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        return render(service.get(id, org.getId()));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Printer input) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        return render(service.create(org.getId(), input));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Printer patch) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        return render(service.update(id, org.getId(), patch));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("ADMIN");
        service.delete(id, org.getId());
        return Map.of("deleted", true);
    }

    @PostMapping("/{id}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        Printer p = service.get(id, org.getId());
        return render(service.heartbeat(p));
    }

    @PostMapping("/{id}/pause")
    public Map<String, Object> pause(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        Printer p = service.get(id, org.getId());
        service.pause(p);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/resume")
    public Map<String, Object> resume(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        Printer p = service.get(id, org.getId());
        service.resume(p);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id) {
        Organization org = orgCtx.currentOrganization();
        orgCtx.requireRole("OPERATOR");
        Printer p = service.get(id, org.getId());
        service.cancel(p);
        return Map.of("ok", true);
    }

    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Organization org = orgCtx.currentOrganization();
        return events.register(org.getId());
    }

    public static Map<String, Object> render(Printer p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("vendor", p.getVendor());
        m.put("model", p.getModel());
        m.put("status", p.getStatus());
        m.put("progress_pct", p.getProgressPct());
        m.put("current_job_id", p.getCurrentJobId());
        m.put("bed_temp_c", p.getBedTempC());
        m.put("hotend_temp_c", p.getHotendTempC());
        m.put(
                "build_volume_mm",
                List.of(p.getBuildVolumeXmm(), p.getBuildVolumeYmm(), p.getBuildVolumeZmm()));
        m.put("supported_material_families", p.getSupportedMaterialFamilies());
        m.put("adapter_type", p.getAdapterType());
        m.put("adapter_url", p.getAdapterUrl());
        m.put("hourly_rate_eur", p.getHourlyRateEur());
        m.put("last_heartbeat_at", p.getLastHeartbeatAt());
        return m;
    }
}
