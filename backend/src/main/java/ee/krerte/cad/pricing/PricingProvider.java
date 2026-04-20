package ee.krerte.cad.pricing;

import reactor.core.publisher.Mono;

/**
 * 3D-print-teenuse partneri pricing API abstraktsioon.
 *
 * <p>Iga Spring bean, mis implementeerib selle interface'i, registreeritakse
 * automaatselt {@link PricingService}-sse ning kutsutakse paralleelselt iga
 * {@code POST /api/pricing/compare} päringu korral.
 *
 * <p>Implementatsioon EI TOHI blokeerida — kasutab {@code WebClient} reactive
 * pipeline'i. {@link PricingService} paneb ise timeout'i peale, nii et siin
 * võid keskenduda ainult päringu-loogikale.
 *
 * <p>Kui API-võti puudub või partner on disable'itud, tagasta
 * {@link PricingQuote#fallback} või {@link PricingQuote#offline} — MITTE viska
 * erandit. Erand peaks tähendama tõelist viga, mitte oodatud konfiguratsiooni-
 * probleemi.
 */
public interface PricingProvider {

    /**
     * Stabiilne, inimloetav identifikaator (URL-is ka kasutatav).
     * Näited: "koda", "printidud", "shapeways", "treatstock".
     */
    String providerId();

    /** Kuva-nimi kasutajaliideses. */
    String displayName();

    /** Kas antud kutse korral saadame päringu (false kui API-võti puudu). */
    boolean enabled();

    /**
     * Teostab hinnapäringu. Tagastatud Mono peab olema non-blocking.
     * Viga ei VII Mono.error()-sse — tagasta
     * {@link PricingQuote#error(String,String,String,long)} asemel.
     */
    Mono<PricingQuote> quote(PricingCompareRequest request);
}
