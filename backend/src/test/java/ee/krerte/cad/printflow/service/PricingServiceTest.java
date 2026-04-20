package ee.krerte.cad.printflow.service;

import ee.krerte.cad.printflow.entity.Material;
import ee.krerte.cad.printflow.entity.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Puhtalt ühiku-test — ei vaja Spring konteksti ega DB-d.
 * Kontrollib hinnastamise valemit:
 *   base = filament + (time/3600) * hourly
 *   unit = max(base, setup) * (1 + margin/100) * rush
 *   line = unit * qty * (1 - volume_discount)
 */
class PricingServiceTest {

    private PricingService svc;
    private Organization org;
    private Material mat;

    @BeforeEach
    void setUp() {
        svc = new PricingService();

        org = new Organization();
        org.setHourlyRateEur(new BigDecimal("3.00"));
        org.setDefaultMarginPct(40);
        org.setDefaultSetupFeeEur(new BigDecimal("5.00"));

        mat = new Material();
        mat.setOrganizationId(1L);
        mat.setName("Generic PLA");
        mat.setFamily("PLA");
        mat.setPricePerKgEur(new BigDecimal("25.00"));
        mat.setDensityGcm3(new BigDecimal("1.24"));
        mat.setSetupFeeEur(new BigDecimal("5.00"));
    }

    @Test
    void volumeDiscount_tiers() {
        assertEquals(0.0,  PricingService.volumeDiscount(1),   0.001);
        assertEquals(0.0,  PricingService.volumeDiscount(4),   0.001);
        assertEquals(0.05, PricingService.volumeDiscount(5),   0.001);
        assertEquals(0.05, PricingService.volumeDiscount(19),  0.001);
        assertEquals(0.10, PricingService.volumeDiscount(20),  0.001);
        assertEquals(0.10, PricingService.volumeDiscount(99),  0.001);
        assertEquals(0.15, PricingService.volumeDiscount(100), 0.001);
        assertEquals(0.15, PricingService.volumeDiscount(500), 0.001);
    }

    @Test
    void calculate_simpleSinglePart_noRushNoDiscount() {
        // 50g PLA * 25€/kg = 1.25€ filament
        // 3600s = 1h * 3€/h = 3.00€ time
        // base = 4.25€, > setup 5€ — ei, 4.25 < 5, seega floor = 5€
        // margin 40%: unit = 5 * 1.4 = 7.00€
        // qty 1, no discount
        PricingService.LinePricing p = svc.calculate(org, mat, 1, 3600, 50, BigDecimal.ONE);

        assertEquals(new BigDecimal("1.25"), p.filamentCostEur);
        assertEquals(new BigDecimal("3.00"), p.timeCostEur);
        assertEquals(new BigDecimal("5.00"), p.setupFeeEur);
        assertEquals(new BigDecimal("7.00"), p.unitPriceEur);
        assertEquals(new BigDecimal("7.00"), p.totalEur);
        assertEquals(40, p.marginPct);
        assertEquals(0, p.volumeDiscountPct);
    }

    @Test
    void calculate_largeJobAboveSetup() {
        // 200g filament * 25€ = 5.00€
        // 4h * 3€ = 12.00€
        // base = 17.00€, margin 40% → unit = 23.80€, qty 1
        PricingService.LinePricing p = svc.calculate(org, mat, 1, 14400, 200, BigDecimal.ONE);

        assertEquals(new BigDecimal("5.00"), p.filamentCostEur);
        assertEquals(new BigDecimal("12.00"), p.timeCostEur);
        assertEquals(new BigDecimal("23.80"), p.unitPriceEur);
    }

    @Test
    void calculate_rushMultiplier30Pct() {
        // base = max(4.25, 5) = 5
        // unit = 5 * 1.4 * 1.3 = 9.10€
        PricingService.LinePricing p = svc.calculate(org, mat, 1, 3600, 50, new BigDecimal("1.30"));
        assertEquals(new BigDecimal("9.10"), p.unitPriceEur);
    }

    @Test
    void calculate_volumeDiscount_100qty() {
        // unit = 7€, 100 qty -> total = 7*100*(1-0.15) = 595€
        PricingService.LinePricing p = svc.calculate(org, mat, 100, 3600, 50, BigDecimal.ONE);
        assertEquals(new BigDecimal("7.00"), p.unitPriceEur);
        assertEquals(new BigDecimal("595.00"), p.totalEur);
        assertEquals(15, p.volumeDiscountPct);
    }

    @Test
    void calculate_volumeDiscount_20qty() {
        // unit = 7€, 20 qty -> total = 7*20*(1-0.10) = 126€
        PricingService.LinePricing p = svc.calculate(org, mat, 20, 3600, 50, BigDecimal.ONE);
        assertEquals(new BigDecimal("126.00"), p.totalEur);
        assertEquals(10, p.volumeDiscountPct);
    }

    @Test
    void calculate_usesFallbackHourlyWhenOrgHasNone() {
        Organization orgNoRate = new Organization();
        orgNoRate.setDefaultMarginPct(30);
        orgNoRate.setDefaultSetupFeeEur(new BigDecimal("3.00"));
        // org.getHourlyRateEur() == null, kuid mat.organizationId != null → loeb org-i
        // aga org hourly is null → NPE risk. Meie valem kasutab fallback 2.50
        // ainult kui material.getOrganizationId() == null. Kontrollime käitumist:
        Material ownerMat = new Material();
        ownerMat.setOrganizationId(null);  // "system material"
        ownerMat.setPricePerKgEur(new BigDecimal("25.00"));
        ownerMat.setSetupFeeEur(new BigDecimal("3.00"));

        PricingService.LinePricing p = svc.calculate(orgNoRate, ownerMat, 1, 3600, 50, BigDecimal.ONE);
        // filament 1.25 + time (2.50€ fallback) = 3.75, setup 3€ → base 3.75
        // unit = 3.75 * 1.30 = 4.875 → 4.88
        assertEquals(new BigDecimal("4.88"), p.unitPriceEur);
    }
}
