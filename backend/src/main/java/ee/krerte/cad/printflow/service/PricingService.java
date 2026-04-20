package ee.krerte.cad.printflow.service;

import ee.krerte.cad.printflow.entity.Material;
import ee.krerte.cad.printflow.entity.Organization;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Arvutab quote-line hinna slicer-tulemusest + materjali hinnast + organisatsiooni
 * hourly-rate'ist + margin'ist.
 *
 * Valem:
 *   base       = filament_cost_eur + (print_time_sec / 3600) * hourly_rate
 *   unit       = max(base, setup_fee) * (1 + margin_pct/100) * rush_multiplier
 *   line_total = unit * quantity * (1 - volume_discount(quantity))
 */
@Service
public class PricingService {

    /** Arvutab ühe line hinna. */
    public LinePricing calculate(
            Organization org,
            Material material,
            int quantity,
            double printTimeSec,
            double filamentG,
            BigDecimal rushMultiplier
    ) {
        // 1) materjali-hind
        BigDecimal filamentCost = material.getPricePerKgEur()
                .multiply(BigDecimal.valueOf(filamentG))
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);

        // 2) masinahind
        BigDecimal hourlyRate = material.getOrganizationId() != null
                ? org.getHourlyRateEur() : new BigDecimal("2.50");
        BigDecimal timeCost = hourlyRate
                .multiply(BigDecimal.valueOf(printTimeSec / 3600.0))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal base = filamentCost.add(timeCost);

        // 3) setup fee
        BigDecimal setup = material.getSetupFeeEur() != null
                ? material.getSetupFeeEur()
                : org.getDefaultSetupFeeEur();

        if (base.compareTo(setup) < 0) base = setup;

        // 4) margin
        int marginPct = org.getDefaultMarginPct() != null ? org.getDefaultMarginPct() : 40;
        BigDecimal marginMult = BigDecimal.ONE.add(BigDecimal.valueOf(marginPct).divide(BigDecimal.valueOf(100)));
        BigDecimal unit = base.multiply(marginMult);

        // 5) rush
        if (rushMultiplier != null && rushMultiplier.compareTo(BigDecimal.ONE) > 0) {
            unit = unit.multiply(rushMultiplier);
        }

        unit = unit.setScale(2, RoundingMode.HALF_UP);

        // 6) quantity + volume discount
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discount = BigDecimal.valueOf(volumeDiscount(quantity));
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            total = total.multiply(BigDecimal.ONE.subtract(discount));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        LinePricing p = new LinePricing();
        p.filamentCostEur = filamentCost.setScale(2, RoundingMode.HALF_UP);
        p.timeCostEur = timeCost.setScale(2, RoundingMode.HALF_UP);
        p.setupFeeEur = setup;
        p.unitPriceEur = unit;
        p.totalEur = total;
        p.volumeDiscountPct = discount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
        p.marginPct = marginPct;
        return p;
    }

    /** Mahupõhine allahindlus. 1-4: 0%, 5-19: 5%, 20-99: 10%, 100+: 15%. */
    public static double volumeDiscount(int quantity) {
        if (quantity >= 100) return 0.15;
        if (quantity >= 20) return 0.10;
        if (quantity >= 5) return 0.05;
        return 0.0;
    }

    public static class LinePricing {
        public BigDecimal filamentCostEur;
        public BigDecimal timeCostEur;
        public BigDecimal setupFeeEur;
        public BigDecimal unitPriceEur;
        public BigDecimal totalEur;
        public Integer volumeDiscountPct;
        public Integer marginPct;
    }
}
