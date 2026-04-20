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
 * 3DKoda.ee — kohalik Eesti teenus, parimad hinnad ja 1-3 päeva tarne EE-sisene.
 *
 * <p>API dokumentatsioon (hetkel platvormi-siseselt): v1 expect'ib JSON payload'i
 * {@code { "volume_mm3", "weight_g", "material", "delivery_country" }} ja tagastab
 * {@code { "price_eur_incl_vat", "delivery_days_min", "delivery_days_max", "order_url" }}.
 */
@Component
public class KodaPricingProvider extends AbstractHttpPricingProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.pricing.koda.api-key:}")
    private String apiKey;

    @Value("${app.pricing.koda.base-url:https://api.3dkoda.ee}")
    private String baseUrl;

    public KodaPricingProvider(WebClient webClient) {
        super(webClient);
    }

    @Override public String providerId()       { return "koda"; }
    @Override public String displayName()      { return "3DKoda.ee"; }
    @Override public boolean enabled()         { return apiKey != null && !apiKey.isBlank(); }

    // EE-sisene teenus — kiireim tarne
    @Override protected int defaultDeliveryDaysMin() { return 1; }
    @Override protected int defaultDeliveryDaysMax() { return 3; }
    // Agressiivne turuhind kohaliku konkurentsi tõttu
    @Override protected double providerPriceMultiplier() { return 0.92; }

    @Override
    protected Mono<PricingQuote> callApi(PricingCompareRequest req) {
        long t0 = System.currentTimeMillis();

        ObjectNode body = mapper.createObjectNode();
        body.put("volume_mm3", req.volumeCm3() * 1000.0);
        body.put("weight_g", req.weightG());
        body.put("material", req.materialOrDefault());
        body.put("infill_percent", req.infillOrDefault());
        body.put("copies", req.copiesOrDefault());
        body.put("delivery_country", req.countryOrDefault());

        return webClient.post()
                .uri(baseUrl + "/v1/quote")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(j -> PricingQuote.ok(
                        providerId(), displayName(),
                        j.path("price_eur_incl_vat").asDouble(),
                        "EUR",
                        j.path("delivery_days_min").asInt(defaultDeliveryDaysMin()),
                        j.path("delivery_days_max").asInt(defaultDeliveryDaysMax()),
                        req.materialOrDefault(),
                        j.path("order_url").asText(baseUrl + "/order"),
                        System.currentTimeMillis() - t0
                ));
    }
}
