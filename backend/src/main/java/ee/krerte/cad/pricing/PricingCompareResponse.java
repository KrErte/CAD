package ee.krerte.cad.pricing;

import java.util.Comparator;
import java.util.List;

/**
 * Vastus POST /api/pricing/compare-lt — nimekiri iga partneri quote'iga pluss
 * tuletatud väljad (cheapest, fastest) et frontend ei peaks uuesti arvutama.
 */
public record PricingCompareResponse(
        List<PricingQuote> quotes,
        String cheapestProviderId,
        String fastestProviderId,
        int totalMillis
) {

    public static PricingCompareResponse of(List<PricingQuote> quotes, int totalMs) {
        String cheapest = quotes.stream()
                .filter(q -> q.status() == PricingQuote.Status.OK || q.status() == PricingQuote.Status.FALLBACK)
                .filter(q -> q.priceEur() != null)
                .min(Comparator.comparingDouble(PricingQuote::priceEur))
                .map(PricingQuote::providerId)
                .orElse(null);

        String fastest = quotes.stream()
                .filter(q -> q.status() == PricingQuote.Status.OK)
                .filter(q -> q.deliveryDaysMin() != null)
                .min(Comparator.comparingInt(PricingQuote::deliveryDaysMin))
                .map(PricingQuote::providerId)
                .orElse(null);

        return new PricingCompareResponse(quotes, cheapest, fastest, totalMs);
    }
}
