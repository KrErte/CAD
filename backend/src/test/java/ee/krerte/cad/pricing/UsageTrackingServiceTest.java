package ee.krerte.cad.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageTrackingServiceTest {

    private UsageTrackingService service;

    @BeforeEach
    void setUp() {
        Map<PricingPlan, PlanLimits> limits = new PlanConfig().planLimitsMap();
        // null Redis → in-memory fallback
        service = new UsageTrackingService(limits, null);
    }

    @Test
    void makerAllowedUpToLimit() {
        long userId = 42L;
        for (int i = 1; i <= 100; i++) {
            var result =
                    service.checkAndRecord(
                            userId, PricingPlan.MAKER, UsageTrackingService.UsageKind.GENERATION);
            assertTrue(result.allowed(), "Should be allowed at count " + i);
        }
        // 101st should be denied
        var denied =
                service.checkAndRecord(
                        userId, PricingPlan.MAKER, UsageTrackingService.UsageKind.GENERATION);
        assertFalse(denied.allowed());
        assertEquals(101, denied.current());
        assertEquals(100, denied.limit());
    }

    @Test
    void demoDailyLimitIs2() {
        var r1 = service.checkDemo("192.168.1.1");
        assertTrue(r1.allowed());
        assertEquals(1, r1.current());

        var r2 = service.checkDemo("192.168.1.1");
        assertTrue(r2.allowed());
        assertEquals(2, r2.current());

        var r3 = service.checkDemo("192.168.1.1");
        assertFalse(r3.allowed());
        assertEquals(3, r3.current());
        assertEquals(2, r3.limit());
    }

    @Test
    void differentIpsTrackedSeparately() {
        service.checkDemo("10.0.0.1");
        service.checkDemo("10.0.0.1");
        // IP1 exhausted
        assertFalse(service.checkDemo("10.0.0.1").allowed());
        // IP2 still has quota
        assertTrue(service.checkDemo("10.0.0.2").allowed());
    }

    @Test
    void unlimitedPlanAlwaysAllowed() {
        for (int i = 0; i < 50; i++) {
            var result =
                    service.checkAndRecord(
                            99L,
                            PricingPlan.BUREAU_FACTORY,
                            UsageTrackingService.UsageKind.GENERATION);
            assertTrue(result.allowed());
            assertEquals(-1, result.limit());
        }
    }

    @Test
    void sha256ProducesConsistentHash() {
        String h1 = UsageTrackingService.sha256("test");
        String h2 = UsageTrackingService.sha256("test");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
    }
}
