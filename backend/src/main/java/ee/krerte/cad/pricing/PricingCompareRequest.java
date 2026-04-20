package ee.krerte.cad.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Klient esitab STL-ist tuletatud sisendi, millelt partnerid arvestavad hinna.
 *
 * <p>STL-i ennast serveri vahepeal ei säilita — kasutame ainult juba sliced metrics'ist
 * saadud arvväärtusi (volume_mm3, weight_g). See on oluliselt kiirem kui iga partnerile
 * STL'i hashida ja üles laadida. Reaalne tellimus (mitte quote) laadib STL-i partneri
 * API-le ainult siis kui kasutaja on hinna valinud.
 */
public record PricingCompareRequest(
        @NotNull @Min(1)        Double volumeCm3,
        @NotNull @Min(1)        Double weightG,
        @NotNull                BoundingBox bbox,
                                String material,   // "PLA", "PETG", "ABS", "TPU", "RESIN"
                                Integer infillPercent, // 10..100
                                Integer copies,        // default 1
                                String countryCode,    // "EE" default — mõjutab saatmiskulu
                                String userEmail       // optional — partner võib tagastada isiklikku soodustust
) {

    public record BoundingBox(double x, double y, double z) {}

    public String materialOrDefault()   { return material == null || material.isBlank() ? "PLA" : material; }
    public int infillOrDefault()        { return infillPercent == null ? 20 : infillPercent; }
    public int copiesOrDefault()        { return copies == null || copies < 1 ? 1 : copies; }
    public String countryOrDefault()    { return countryCode == null || countryCode.isBlank() ? "EE" : countryCode; }
}
