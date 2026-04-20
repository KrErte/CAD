package ee.krerte.cad.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Ühine alusklass HTTP-põhistele providereile. Kapseldab:
 * <ul>
 *   <li>{@code enabled()}-kontrolli (API-võti seatud vs mitte),</li>
 *   <li>timeout'i (iga provideri kohta eraldi),</li>
 *   <li>logimise + turvalise error->fallback üleminek,</li>
 *   <li>heuristilise hinnangu arvutuse (volume × per-cm³ rate).</li>
 * </ul>
 *
 * <p>Konkreetne provider implementeerib ainult {@link #callApi(PricingCompareRequest)}
 * meetodit; ülejäänu on juba korras.
 */
public abstract class AbstractHttpPricingProvider implements PricingProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final WebClient webClient;

    protected AbstractHttpPricingProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Maksimaalne aeg, mille me ühelt providerilt ootame. Vaikimisi 5s —
     * aggressive, et UI ei jääks kinni. Override, kui provider on tuntult aeglane.
     */
    protected Duration timeout() {
        return Duration.ofSeconds(5);
    }

    /**
     * Eur/cm³ heuristika PLA-le — kasutame ainult fallback-stsenaariumis, et
     * kasutaja näeks UI-s vähemalt KÄRA numbrit ja et testimine toimiks ilma
     * API-võtmeteta. Reaalsed partnerid annavad oma API kaudu täpse hinna.
     *
     * <p>Kalibreeritud 2026-04 andmetega: 3DHubs EU mean ≈ 0.70 €/g PLA
     * (incl VAT + shipping). Tihedus 1.24 g/cm³ → ~0.87 €/cm³.
     */
    protected double heuristicEurPerCm3(String material) {
        return switch (material == null ? "PLA" : material.toUpperCase()) {
            case "PETG"  -> 1.05;
            case "ABS"   -> 1.10;
            case "TPU"   -> 1.45;
            case "RESIN" -> 2.20;
            default      -> 0.87; // PLA
        };
    }

    /**
     * Providerid, kes konkurentsi tõttu võtavad kallimat marginaali
     * (nt Shapeways on tuntult kõige kallim). 1.0 = tavaline, > 1.0 = kallim.
     */
    protected double providerPriceMultiplier() {
        return 1.0;
    }

    /**
     * Pikem tarneaeg EU-sisese postiteenuse tõttu (tüüpiline 3-5 päeva).
     */
    protected int defaultDeliveryDaysMin() { return 3; }
    protected int defaultDeliveryDaysMax() { return 7; }

    /**
     * Konkreetse provideri API-kutse. Implementatsioon peab tagastama
     * reactive Mono<PricingQuote>, ei tohi blokeerida.
     */
    protected abstract Mono<PricingQuote> callApi(PricingCompareRequest request);

    @Override
    public final Mono<PricingQuote> quote(PricingCompareRequest request) {
        long t0 = System.currentTimeMillis();

        if (!enabled()) {
            log.debug("{} disabled — returning fallback heuristic", providerId());
            return Mono.just(buildFallback(request, t0));
        }

        return callApi(request)
                .timeout(timeout())
                .onErrorResume(ex -> {
                    if (ex instanceof WebClientResponseException wex) {
                        log.warn("{} HTTP {}: {}", providerId(), wex.getStatusCode(), wex.getResponseBodyAsString());
                    } else {
                        log.warn("{} call failed: {}", providerId(), ex.getMessage());
                    }
                    // Graceful: tagastame fallback'i, et kasutajaliides ei jääks tühjaks.
                    return Mono.just(buildFallback(request, t0));
                })
                .defaultIfEmpty(buildFallback(request, t0));
    }

    protected PricingQuote buildFallback(PricingCompareRequest req, long t0) {
        double pricePerCm3 = heuristicEurPerCm3(req.materialOrDefault()) * providerPriceMultiplier();
        // Infill mõjutab materjali kulu, mitte kogu hinda — eeldame 20% infill baas'iks.
        double infillFactor = 0.7 + 0.3 * (req.infillOrDefault() / 100.0);
        double price = req.volumeCm3() * pricePerCm3 * infillFactor * req.copiesOrDefault();
        // Ümardame 10 sendini, et ei näeks tehislikult täpset mulje.
        price = Math.round(price * 10.0) / 10.0;

        long ms = System.currentTimeMillis() - t0;
        return PricingQuote.fallback(providerId(), displayName(), price, req.materialOrDefault(), ms);
    }
}
