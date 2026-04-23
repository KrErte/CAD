package ee.krerte.cad.pricing;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanConfig {

    @Bean
    public Map<PricingPlan, PlanLimits> planLimitsMap() {
        var m = new EnumMap<PricingPlan, PlanLimits>(PricingPlan.class);

        // ── Makers ──
        m.put(PricingPlan.DEMO, PlanLimits.demo(2));
        m.put(PricingPlan.MAKER, PlanLimits.maker(100, 30, 10));
        m.put(PricingPlan.CREATOR, PlanLimits.maker(500, 150, 50));

        // ── Print Bureaus ──
        m.put(PricingPlan.BUREAU_STARTER, PlanLimits.bureau(50, 1));
        m.put(PricingPlan.BUREAU_STUDIO, PlanLimits.bureau(500, 10));
        m.put(PricingPlan.BUREAU_FACTORY, PlanLimits.bureau(-1, -1));
        m.put(PricingPlan.BUREAU_ENTERPRISE, PlanLimits.bureau(-1, -1));

        // ── Developers ──
        m.put(PricingPlan.DEV_TRIAL, PlanLimits.dev(-1, 60, 14, 500));
        m.put(PricingPlan.DEV_GROWTH, PlanLimits.dev(1000, 60, 0, -1));
        m.put(PricingPlan.DEV_BUSINESS, PlanLimits.dev(5000, 300, 0, -1));

        return Map.copyOf(m);
    }
}
