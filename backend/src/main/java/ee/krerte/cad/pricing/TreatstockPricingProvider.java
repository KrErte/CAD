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
 * Treatstock — marketplace-tüüpi platvorm, kus erinevad printerid pakuvad
 * oma hinda. Tagastab MITMIK-hindu, aga me valime siin kõige odavama
 * üksiku tulemuse vaikimisi.
 */
@Component
public class TreatstockPricingProvider extends AbstractHttpPricingProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.pricing.treatstock.api-key:}")
    private String apiKey;

    @Value("${app.pricing.treatstock.base-url:https://api.treatstock.com}")
    private String baseUrl;

    public TreatstockPricingProvider(WebClient webClient) {
        super(webClient);
    }

    @Override public String providerId()       { return "treatstock"; }
    @Override public String displayName()      { return "Treatstock"; }
    @Override public boolean enabled()         { return apiKey != null && !apiKey.isBlank(); }

    @Override protected int defaultDeliveryDaysMin() { return 4; }
    @Override protected int defaultDeliveryDaysMax() { return 10; }
    @Override protected double providerPriceMultiplier() { return 1.15; }

    @Override
    protected Mono<PricingQuote> callApi(PricingCompareRequest req) {
        long t0 = System.currentTimeMillis();

        ObjectNode body = mapper.createObjectNode();
        body.put("volume_cm3", req.volumeCm3());
        body.put("weight_g", req.weightG());
        body.put("material", req.materialOrDefault().toLowerCase());
        body.put("infill", req.infillOrDefault());
        body.put("destination_country", req.countryOrDefault());
        body.put("bbox_x_mm", req.bbox().x());
        body.put("bbox_y_mm", req.bbox().y());
        body.put("bbox_z_mm", req.bbox().z());

        return webClient.post()
                .uri(baseUrl + "/v2/quotes/compare")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(j -> {
                    // Treatstock tagastab offers[] — valime madalaima
                    JsonNode offers = j.path("offers");
                    if (!offers.isArray() || offers.isEmpty()) {
                        return PricingQuote.error(providerId(), displayName(),
                                "Treatstock returned no offers", System.currentTimeMillis() - t0);
                    }
                    JsonNode best = offers.get(0);
                    for (JsonNode o : offers) {
                        if (o.path("total_price_eur").asDouble() <
                                best.path("total_price_eur").asDouble()) {
                            best = o;
                        }
                    }
                    return PricingQuote.ok(
                            providerId(), displayName(),
                            best.path("total_price_eur").asDouble(),
                            "EUR",
                            best.path("shipping_days_min").asInt(defaultDeliveryDaysMin()),
                            best.path("shipping_days_max").asInt(defaultDeliveryDaysMax()),
                            req.materialOrDefault(),
                            best.path("checkout_url").asText("https://treatstock.com/checkout"),
                            System.currentTimeMillis() - t0
                    );
                });
    }
}
