package ee.krerte.cad.printflow.controller;

import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.service.AnalyticsService;
import ee.krerte.cad.printflow.service.OrganizationContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Tootmise KPI-d juhtpaneelile — revenue, OEE, success rate, top materjalid. */
@RestController
@RequestMapping("/api/printflow/analytics")
public class AnalyticsController {

    private final AnalyticsService svc;
    private final OrganizationContext orgCtx;

    public AnalyticsController(AnalyticsService s, OrganizationContext o) {
        this.svc = s;
        this.orgCtx = o;
    }

    @GetMapping("/kpi")
    public Map<String, Object> kpi() {
        Organization org = orgCtx.currentOrganization();
        return svc.kpi(org.getId());
    }

    @GetMapping("/top-materials")
    public List<Map<String, Object>> topMaterials() {
        Organization org = orgCtx.currentOrganization();
        return svc.topMaterials(org.getId());
    }

    @GetMapping("/revenue")
    public List<Map<String, Object>> revenue(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        Organization org = orgCtx.currentOrganization();
        if (days < 1) days = 1;
        if (days > 365) days = 365;
        return svc.revenueByDay(org.getId(), days);
    }
}
