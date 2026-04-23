package ee.krerte.cad.pricing;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoint — no auth required. Returns all pricing plans as JSON. */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final Map<PricingPlan, PlanLimits> limitsMap;

    public PricingController(Map<PricingPlan, PlanLimits> limitsMap) {
        this.limitsMap = limitsMap;
    }

    public record PlanInfo(
            String id,
            String segment,
            String name,
            String price,
            boolean featured,
            PlanLimits limits) {}

    @GetMapping("/plans")
    public List<PlanInfo> plans() {
        return List.of(
                // ── Makers ──
                new PlanInfo("MAKER", "makers", "Maker", "Free", false, limitsMap.get(PricingPlan.MAKER)),
                new PlanInfo(
                        "CREATOR",
                        "makers",
                        "Creator",
                        "29.99 EUR/mo",
                        true,
                        limitsMap.get(PricingPlan.CREATOR)),

                // ── Bureaus ──
                new PlanInfo(
                        "BUREAU_STARTER",
                        "bureaus",
                        "Starter",
                        "49 EUR/mo",
                        false,
                        limitsMap.get(PricingPlan.BUREAU_STARTER)),
                new PlanInfo(
                        "BUREAU_STUDIO",
                        "bureaus",
                        "Studio",
                        "149 EUR/mo",
                        true,
                        limitsMap.get(PricingPlan.BUREAU_STUDIO)),
                new PlanInfo(
                        "BUREAU_FACTORY",
                        "bureaus",
                        "Factory",
                        "399 EUR/mo",
                        false,
                        limitsMap.get(PricingPlan.BUREAU_FACTORY)),
                new PlanInfo(
                        "BUREAU_ENTERPRISE",
                        "bureaus",
                        "Enterprise",
                        "Custom",
                        false,
                        limitsMap.get(PricingPlan.BUREAU_ENTERPRISE)),

                // ── Developers ──
                new PlanInfo(
                        "DEV_TRIAL",
                        "developers",
                        "Trial",
                        "Free / 14 days",
                        false,
                        limitsMap.get(PricingPlan.DEV_TRIAL)),
                new PlanInfo(
                        "DEV_GROWTH",
                        "developers",
                        "Growth",
                        "79 EUR/mo",
                        true,
                        limitsMap.get(PricingPlan.DEV_GROWTH)),
                new PlanInfo(
                        "DEV_BUSINESS",
                        "developers",
                        "Business",
                        "249 EUR/mo",
                        false,
                        limitsMap.get(PricingPlan.DEV_BUSINESS)));
    }
}
