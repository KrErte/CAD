package ee.krerte.cad.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingPlanTest {

    private final Map<PricingPlan, PlanLimits> limits = new PlanConfig().planLimitsMap();

    @Test
    void allPlansHaveLimits() {
        for (PricingPlan plan : PricingPlan.values()) {
            assertNotNull(limits.get(plan), "Missing limits for " + plan);
        }
    }

    @Test
    void demoHasDailyLimit2() {
        PlanLimits demo = limits.get(PricingPlan.DEMO);
        assertEquals(2, demo.dailyLimit());
        assertEquals(0, demo.generationsPerMonth());
        assertEquals(0, demo.reviewsPerMonth());
        assertEquals(0, demo.meshyPerMonth());
    }

    @Test
    void makerLimitsCorrect() {
        PlanLimits maker = limits.get(PricingPlan.MAKER);
        assertEquals(100, maker.generationsPerMonth());
        assertEquals(30, maker.reviewsPerMonth());
        assertEquals(10, maker.meshyPerMonth());
    }

    @Test
    void creatorLimitsCorrect() {
        PlanLimits creator = limits.get(PricingPlan.CREATOR);
        assertEquals(500, creator.generationsPerMonth());
        assertEquals(150, creator.reviewsPerMonth());
        assertEquals(50, creator.meshyPerMonth());
    }

    @Test
    void bureauStarterLimitsCorrect() {
        PlanLimits starter = limits.get(PricingPlan.BUREAU_STARTER);
        assertEquals(50, starter.ordersPerMonth());
        assertEquals(1, starter.maxPrinters());
    }

    @Test
    void bureauStudioLimitsCorrect() {
        PlanLimits studio = limits.get(PricingPlan.BUREAU_STUDIO);
        assertEquals(500, studio.ordersPerMonth());
        assertEquals(10, studio.maxPrinters());
    }

    @Test
    void bureauFactoryIsUnlimited() {
        PlanLimits factory = limits.get(PricingPlan.BUREAU_FACTORY);
        assertEquals(-1, factory.ordersPerMonth());
        assertEquals(-1, factory.maxPrinters());
    }

    @Test
    void devTrialHas14DayTrial() {
        PlanLimits trial = limits.get(PricingPlan.DEV_TRIAL);
        assertEquals(14, trial.trialDays());
        assertEquals(500, trial.totalGenerations());
        assertEquals(60, trial.ratePerMinute());
    }

    @Test
    void devGrowthLimitsCorrect() {
        PlanLimits growth = limits.get(PricingPlan.DEV_GROWTH);
        assertEquals(1000, growth.generationsPerMonth());
        assertEquals(60, growth.ratePerMinute());
    }

    @Test
    void devBusinessLimitsCorrect() {
        PlanLimits biz = limits.get(PricingPlan.DEV_BUSINESS);
        assertEquals(5000, biz.generationsPerMonth());
        assertEquals(300, biz.ratePerMinute());
    }

    @Test
    void legacyMapping() {
        assertEquals(PricingPlan.MAKER, PricingPlan.fromLegacy("FREE"));
        assertEquals(PricingPlan.CREATOR, PricingPlan.fromLegacy("PRO"));
        assertEquals(PricingPlan.DEV_GROWTH, PricingPlan.fromLegacy("BUSINESS"));
        assertEquals(PricingPlan.DEMO, PricingPlan.fromLegacy(null));
        assertEquals(PricingPlan.DEMO, PricingPlan.fromLegacy("UNKNOWN"));
    }
}
