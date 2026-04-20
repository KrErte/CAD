package ee.krerte.cad.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Üks hinnapakkumine ühelt 3D-print-teenuse partnerilt.
 *
 * <p>Kõik väljad on optional peale {@code providerId} ja {@code status} — kui
 * teenus on ajutiselt maas või API-võti puudub, tagastame {@code status=OFFLINE}
 * ning kasutajaliides filtreerib need "available"-kihtist välja.
 *
 * <p>Hind on alati eurodes, INCL käibemaks (kasutaja lõpphind). {@code priceMinorUnits}
 * on sama hind minorunit'ides (sendid), et vältida ujukoma-vigu frontend'is.
 */
public record PricingQuote(
        String providerId,           // "koda", "printidud", "shapeways", "treatstock"
        String providerDisplayName,  // "3DKoda.ee", "3D Prinditud", "Shapeways", "Treatstock"
        Status status,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double priceEur,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long priceMinorUnits,
        @JsonInclude(JsonInclude.Include.NON_NULL) String currency,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer deliveryDaysMin,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer deliveryDaysMax,
        @JsonInclude(JsonInclude.Include.NON_NULL) String material,
        @JsonInclude(JsonInclude.Include.NON_NULL) String orderUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) String notes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        long responseMillis
) {

    public enum Status {
        /** Päring õnnestus, hind käes. */
        OK,
        /** Partneri API tagastas vea või timeout. */
        ERROR,
        /** API-võtit pole seadistatud — saime ainult heuristilise hinnangu. */
        FALLBACK,
        /** Provider on disabled configi kaudu. */
        OFFLINE
    }

    public static PricingQuote ok(String id, String name, double priceEur, String currency,
                                   int dMin, int dMax, String material, String orderUrl, long ms) {
        return new PricingQuote(id, name, Status.OK, priceEur,
                Math.round(priceEur * 100), currency, dMin, dMax, material, orderUrl,
                null, null, ms);
    }

    public static PricingQuote fallback(String id, String name, double priceEur, String material, long ms) {
        return new PricingQuote(id, name, Status.FALLBACK, priceEur,
                Math.round(priceEur * 100), "EUR", null, null, material, null,
                "Heuristiline hinnang — API-võti seadistamata", null, ms);
    }

    public static PricingQuote error(String id, String name, String err, long ms) {
        return new PricingQuote(id, name, Status.ERROR, null, null, null, null, null,
                null, null, null, err, ms);
    }

    public static PricingQuote offline(String id, String name) {
        return new PricingQuote(id, name, Status.OFFLINE, null, null, null, null, null,
                null, null, null, null, 0L);
    }
}
