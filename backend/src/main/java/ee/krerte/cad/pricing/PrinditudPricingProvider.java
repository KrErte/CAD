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
 * 3DPrinditud.ee — teine Eesti teenus, natuke kõrgem hind, aga suurem seadmepark.
 */
@Component
public class PrinditudPricingProvider extends AbstractHttpPricingProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.pricing.printidud.api-key:}")
    private String apiKey;

    @Value("${app.pricing.printidud.base-url:https://api.3dprinditud.ee}")
    private String baseUrl;

    public PrinditudPricingProvider(WebClient webClient) {
        super(webClient);
    }

    @Override public String providerId()       { return "printidud"; }
    @Override public String displayName()      { return "3D Prinditud"; }
    @Override public boolean enabled()         { return apiKey != null && !apiKey.isBlank(); }

    @Override protected int defaultDeliveryDaysMin() { return 2; }
    @Override protected int defaultDeliveryDaysMax() { return 5; }
    @Override protected double providerPriceMultiplier() { return 1.05; }

    @Override
    protected Mono<PricingQuote> callApi(PricingCompareRequest req) {
        long t0 = System.currentTimeMillis();

        ObjectNode body = mapper.createObjectNode();
        body.put("volume_cm3", req.volumeCm3());
        body.put("weight_g", req.weightG());
        ObjectNode bbox = body.putObject("bbox_mm");
        bbox.put("x", req.bbox().x());
        bbox.put("y", req.bbox().y());
        bbox.put("z", req.bbox().z());
        body.put("material_code", mapMaterial(req.materialOrDefault()));
        body.put("infill_pct", req.infillOrDefault());
        body.put("quantity", req.copiesOrDefault());

        return webClient.post()
                .uri(baseUrl + "/api/pricing")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(j -> PricingQuote.ok(
                        providerId(), displayName(),
                        j.path("total_eur").asDouble(),
                        "EUR",
                        j.path("lead_days").path("min").asInt(defaultDeliveryDaysMin()),
                        j.path("lead_days").path("max").asInt(defaultDeliveryDaysMax()),
                        req.materialOrDefault(),
                        j.path("checkout_url").asText(baseUrl + "/checkout"),
                        System.currentTimeMillis() - t0
                ));
    }

    private String mapMaterial(String m) {
        return switch (m.toUpperCase()) {
            case "PLA"   -> "PLA_STD";
            case "PETG"  -> "PETG_STD";
            case "ABS"   -> "ABS_V0";
            case "TPU"   -> "TPU_95A";
            case "RESIN" -> "RSN_ST";
            default      -> "PLA_STD";
        };
    }
}
