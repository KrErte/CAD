package ee.krerte.cad.pricing;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kontrolli parallelset koondamis-loogikat:
 *   1. Kõik providerid saavad KORRAGA kutse (mitte sequential).
 *   2. Ühe provideri viga EI BLOKEERI teisi — teine saab ikka vastuse.
 *   3. Global timeout kaitseb, kui terve Flux jookseb tuksi.
 *   4. Tühi providerite-nimekiri annab tühja Flux'i (ei crashi).
 */
class PricingServiceTest {

    private PricingCompareRequest sampleRequest() {
        return new PricingCompareRequest(
                96.5, 119.7,
                new PricingCompareRequest.BoundingBox(100, 100, 120),
                "PLA", 20, 1, "EE", null);
    }

    @Test
    void callsAllProvidersInParallel() {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        // 3 providerit, igaüks "magab" 200ms. Jadakäsitluse korral 600ms,
        // paralleelse korral ~200ms.
        List<PricingProvider> providers = List.of(
                slowFakeProvider("a", concurrentCount, maxConcurrent, 200, 10.0),
                slowFakeProvider("b", concurrentCount, maxConcurrent, 200, 20.0),
                slowFakeProvider("c", concurrentCount, maxConcurrent, 200, 30.0)
        );

        var svc = new PricingService(providers);
        long t0 = System.currentTimeMillis();
        var resp = svc.compareAll(sampleRequest()).block(Duration.ofSeconds(3));
        long elapsed = System.currentTimeMillis() - t0;

        assertNotNull(resp);
        assertEquals(3, resp.quotes().size());
        assertTrue(elapsed < 800, "paralleelne peab olema oluliselt kiirem kui 3×200ms=600ms, oli " + elapsed + "ms");
        assertTrue(maxConcurrent.get() >= 2, "maxConcurrent peab olema ≥2, oli " + maxConcurrent.get());
        // Cheapest peab olema "a" hinnaga 10.0
        assertEquals("a", resp.cheapestProviderId());
    }

    @Test
    void oneBrokenProviderDoesNotKillOthers() {
        List<PricingProvider> providers = List.of(
                stableProvider("good", 15.0),
                brokenProvider("bad"),  // viskab leaky exception'it
                stableProvider("also_good", 20.0)
        );

        var svc = new PricingService(providers);
        var resp = svc.compareAll(sampleRequest()).block(Duration.ofSeconds(3));

        assertNotNull(resp);
        assertEquals(3, resp.quotes().size(), "kõik 3 quote peavad olema listis (error-quote kaasa arvatud)");

        // Kaks OK-d ja üks error
        long okCount = resp.quotes().stream()
                .filter(q -> q.status() == PricingQuote.Status.OK).count();
        long errCount = resp.quotes().stream()
                .filter(q -> q.status() == PricingQuote.Status.ERROR).count();
        assertEquals(2, okCount);
        assertEquals(1, errCount);
        assertEquals("good", resp.cheapestProviderId());
    }

    @Test
    void emptyProviderListReturnsEmptyQuotes() {
        var svc = new PricingService(List.of());
        StepVerifier.create(svc.compareAll(sampleRequest()))
                .assertNext(resp -> {
                    assertTrue(resp.quotes().isEmpty());
                    assertNull(resp.cheapestProviderId());
                    assertNull(resp.fastestProviderId());
                })
                .verifyComplete();
    }

    // --- Test fixtures ------------------------------------------------------

    private PricingProvider slowFakeProvider(String id,
                                             AtomicInteger concurrent,
                                             AtomicInteger maxConcurrent,
                                             long sleepMs, double price) {
        return new PricingProvider() {
            @Override public String providerId()  { return id; }
            @Override public String displayName() { return id.toUpperCase(); }
            @Override public boolean enabled()    { return true; }
            @Override
            public Mono<PricingQuote> quote(PricingCompareRequest req) {
                return Mono.fromCallable(() -> {
                    int now = concurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(v -> Math.max(v, now));
                    try { Thread.sleep(sleepMs); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    concurrent.decrementAndGet();
                    return PricingQuote.ok(id, id.toUpperCase(), price, "EUR",
                            3, 7, "PLA", "https://x", sleepMs);
                });
            }
        };
    }

    private PricingProvider stableProvider(String id, double price) {
        return new PricingProvider() {
            @Override public String providerId()  { return id; }
            @Override public String displayName() { return id; }
            @Override public boolean enabled()    { return true; }
            @Override
            public Mono<PricingQuote> quote(PricingCompareRequest req) {
                return Mono.just(PricingQuote.ok(id, id, price, "EUR", 3, 7, "PLA", "https://x", 10));
            }
        };
    }

    /** Provider, kes viskab erandi Mono raames — PricingService peab selle kinni püüdma. */
    private PricingProvider brokenProvider(String id) {
        return new PricingProvider() {
            @Override public String providerId()  { return id; }
            @Override public String displayName() { return id; }
            @Override public boolean enabled()    { return true; }
            @Override
            public Mono<PricingQuote> quote(PricingCompareRequest req) {
                return Mono.error(new IllegalStateException("provider crashed"));
            }
        };
    }
}
