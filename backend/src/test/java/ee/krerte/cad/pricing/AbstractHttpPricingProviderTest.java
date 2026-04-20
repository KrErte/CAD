package ee.krerte.cad.pricing;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kontrolli, et AbstractHttpPricingProvider käsitleb graceful'ilt:
 *   1. enabled()=false → FALLBACK
 *   2. API tagastab Mono.error → FALLBACK (ei leke erandit)
 *   3. API tagastab tühja Mono → FALLBACK
 *   4. Heuristika on deterministlik materjali × ruumala järgi
 */
class AbstractHttpPricingProviderTest {

    private PricingCompareRequest sampleRequest(String material) {
        return new PricingCompareRequest(
                100.0, 124.0,
                new PricingCompareRequest.BoundingBox(100, 100, 100),
                material, 20, 1, "EE", null);
    }

    @Test
    void disabledProviderReturnsFallback() {
        var p = testProvider(false, Mono.just(PricingQuote.ok("x","X",1,"EUR",1,1,"PLA","u",1)));
        var q = p.quote(sampleRequest("PLA")).block();

        assertNotNull(q);
        assertEquals(PricingQuote.Status.FALLBACK, q.status());
        assertNotNull(q.priceEur());
        assertTrue(q.priceEur() > 0);
    }

    @Test
    void apiErrorFallsBackToHeuristic() {
        var p = testProvider(true, Mono.error(new RuntimeException("5xx upstream")));
        var q = p.quote(sampleRequest("PLA")).block();

        assertNotNull(q);
        assertEquals(PricingQuote.Status.FALLBACK, q.status());
        assertNotNull(q.priceEur());
    }

    @Test
    void emptyApiResponseFallsBack() {
        var p = testProvider(true, Mono.empty());
        var q = p.quote(sampleRequest("PLA")).block();

        assertNotNull(q);
        assertEquals(PricingQuote.Status.FALLBACK, q.status());
    }

    @Test
    void fallbackPriceScalesWithVolumeAndMaterial() {
        var p = testProvider(false, Mono.empty());
        var pla  = p.quote(sampleRequest("PLA")).block();
        var tpu  = p.quote(sampleRequest("TPU")).block();

        assertNotNull(pla);
        assertNotNull(tpu);
        // TPU (1.45 €/cm³) peab olema kallim kui PLA (0.87 €/cm³)
        assertTrue(tpu.priceEur() > pla.priceEur(),
                "TPU peab olema kallim kui PLA: " + pla.priceEur() + " vs " + tpu.priceEur());
    }

    @Test
    void timeoutIsApplied() {
        // API kutse 10s — provider timeout 1s → peab fall back'ima kiirelt
        var p = testProviderWithTimeout(Duration.ofSeconds(1),
                Mono.delay(Duration.ofSeconds(10))
                        .then(Mono.just(PricingQuote.ok("x","X",1,"EUR",1,1,"PLA","u",1))));
        long t0 = System.currentTimeMillis();
        var q = p.quote(sampleRequest("PLA")).block();
        long elapsed = System.currentTimeMillis() - t0;

        assertNotNull(q);
        assertEquals(PricingQuote.Status.FALLBACK, q.status());
        assertTrue(elapsed < 3000, "timeout peab tabama ~1s, oli " + elapsed + "ms");
    }

    // --- fixtures -----------------------------------------------------------

    private AbstractHttpPricingProvider testProvider(boolean enabled, Mono<PricingQuote> apiResponse) {
        return new AbstractHttpPricingProvider(null) {
            @Override public String providerId()  { return "test"; }
            @Override public String displayName() { return "Test"; }
            @Override public boolean enabled()    { return enabled; }
            @Override
            protected Mono<PricingQuote> callApi(PricingCompareRequest req) {
                return apiResponse;
            }
        };
    }

    private AbstractHttpPricingProvider testProviderWithTimeout(Duration t, Mono<PricingQuote> apiResponse) {
        return new AbstractHttpPricingProvider(null) {
            @Override public String providerId()  { return "test"; }
            @Override public String displayName() { return "Test"; }
            @Override public boolean enabled()    { return true; }
            @Override protected Duration timeout() { return t; }
            @Override
            protected Mono<PricingQuote> callApi(PricingCompareRequest req) {
                return apiResponse;
            }
        };
    }
}
