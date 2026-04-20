package ee.krerte.cad.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kontrolli, et {@code cheapestProviderId} ja {@code fastestProviderId}
 * tuletised töötavad ka segu korral (OK + FALLBACK + ERROR + OFFLINE).
 */
class PricingCompareResponseTest {

    @Test
    void picksCheapestAmongOkAndFallback() {
        var quotes = List.of(
                PricingQuote.ok("koda", "3DKoda", 10.50, "EUR", 1, 3, "PLA", "https://x", 120),
                PricingQuote.ok("shapeways", "Shapeways", 25.00, "EUR", 7, 14, "PLA", "https://y", 340),
                PricingQuote.fallback("printidud", "3D Prinditud", 12.00, "PLA", 10),
                PricingQuote.error("treatstock", "Treatstock", "500", 5000),
                PricingQuote.offline("other", "Other")
        );
        var resp = PricingCompareResponse.of(quotes, 500);
        assertEquals("koda", resp.cheapestProviderId(),
                "Koda on odavaim OK/FALLBACK-ide seas (10.50 €)");
    }

    @Test
    void picksFastestOnlyFromOk() {
        var quotes = List.of(
                PricingQuote.ok("shapeways", "Shapeways", 25.00, "EUR", 7, 14, "PLA", "https://y", 340),
                PricingQuote.ok("koda", "3DKoda", 10.50, "EUR", 1, 3, "PLA", "https://x", 120),
                // Fallback ei ole "fastest" kandidaat, sest tal pole real delivery info
                PricingQuote.fallback("printidud", "3D Prinditud", 8.00, "PLA", 10)
        );
        var resp = PricingCompareResponse.of(quotes, 500);
        assertEquals("koda", resp.fastestProviderId(),
                "Koda deliveryDaysMin=1 võidab Shapewaysi (7)");
    }

    @Test
    void nullWhenNoCandidates() {
        var quotes = List.of(
                PricingQuote.error("koda", "3DKoda", "timeout", 5000),
                PricingQuote.offline("x", "X")
        );
        var resp = PricingCompareResponse.of(quotes, 100);
        assertNull(resp.cheapestProviderId());
        assertNull(resp.fastestProviderId());
    }
}
