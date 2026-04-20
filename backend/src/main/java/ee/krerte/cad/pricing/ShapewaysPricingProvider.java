package ee.krerte.cad.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Shapeways — suurim globaalne teenus, lai materjalivalik (metall, teatud
 * vaigud), aga kallim ja pikem tarneaeg EU-sse (tavaliselt 7-14 päeva).
 */
@Component
public class ShapewaysPricingProvider extends AbstractHttpPricingProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.pricing.shapeways.api-key:}")
    private String apiKey;

    @Value("${app.pricing.shapeways.base-url:https://api.shapeways.com}")
    private String baseUrl;

    public ShapewaysPricingProvider(WebClient webClient) {
        super(webClient);
    }

    @Override public String providerId()       { return "shapeways"; }
    @Override public String displayName()      { return "Shapeways"; }
    @Override public boolean enabled()         { return apiKey != null && !apiKey.isBlank(); }

    @Override protected int defaultDeliveryDaysMin() { return 7; }
    @Override protected int defaultDeliveryDaysMax() { return 14; }
    @Override protected double providerPriceMultiplier() { return 1.35; }

    @Override
    protected Mono<PricingQuote> callApi(PricingCompareRequest req) {
        long t0 = System.currentTimeMillis();

        ObjectNode body = mapper.createObjectNode();
        // Shapeways API v1 expects cm3, not mm3
        body.put("volume", req.volumeCm3());
        body.put("bbox_x", req.bbox().x());
        body.put("bbox_y", req.bbox().y());
        body.put("bbox_z", req.bbox().z());
        body.put("material_id", mapMaterialId(req.materialOrDefault()));
        body.put("country", req.countryOrDefault());

        return webClient.post()
                .uri(baseUrl + "/models/v1/pricing")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(j -> {
                    // Shapeways tagastab USD-s — konverteerime (kirjeldavalt ~1.08 EUR/USD 2026-04)
                    double usd = j.path("price").asDouble();
                    double eur = usd / 1.08;
                    return PricingQuote.ok(
                            providerId(), displayName(),
                            Math.round(eur * 100.0) / 100.0,
                            "EUR",
                            j.path("ship_days_min").asInt(defaultDeliveryDaysMin()),
                            j.path("ship_days_max").asInt(defaultDeliveryDaysMax()),
                            req.materialOrDefault(),
                            "https://shapeways.com/checkout?ref=ai-cad",
                            System.currentTimeMillis() - t0
                    );
                });
    }

    private int mapMaterialId(String m) {
        // Shapeways material ID-d on sisekoodid — näidised kalibreeritud nende katalogist
        return switch (m.toUpperCase()) {
            case "PLA"   -> 25;  // Natural Versatile Plastic
            case "PETG"  -> 62;
            case "ABS"   -> 75;
            case "TPU"   -> 81;
            case "RESIN" -> 220; // High Definition Detail Plastic
            default      -> 25;
        };
    }
}
