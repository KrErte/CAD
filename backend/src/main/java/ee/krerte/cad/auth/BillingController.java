package ee.krerte.cad.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Creates a Stripe Checkout Session and returns the URL to redirect to.
 *
 * <p>Uus hinnastruktuur (2026):
 * <ul>
 *   <li><b>maker</b> — 12.99 €/kuu või 129 €/a (10.75 €/kuu ekvivalent)</li>
 *   <li><b>pro</b>   — 29.99 €/kuu või 299 €/a (24.90 €/kuu ekvivalent)</li>
 *   <li><b>team</b>  — 79 €/koht/kuu või 65 €/koht/kuu aastas (alates 3 kohast)</li>
 * </ul>
 *
 * <p>Legacy <b>hobi</b> tier mäpitakse automaatselt <b>maker</b>-le kui keegi veel
 * vana vormil klõpsab. See hoiab vana URL/email kampaaniad töös.
 *
 * <p>Konfiguratsioon application.yml:
 * <pre>
 * app:
 *   stripe:
 *     secret-key: sk_live_...
 *     price-maker-monthly: price_...
 *     price-maker-yearly:  price_...
 *     price-pro-monthly:   price_...
 *     price-pro-yearly:    price_...
 *     price-team-monthly:  price_...
 *     price-team-yearly:   price_...
 * </pre>
 *
 * <p>Kui Stripe pole seadistatud, tagastame 503 sõbraliku teatega — frontend kuvab
 * selle kasutajale (nt "Stripe on seadistamisel, tule peagi tagasi").
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final UserRepository users;
    private final String secretKey;
    private final String frontendUrl;
    private final WebClient stripe;
    private final Map<String, String> priceMap;

    public BillingController(UserRepository users,
                             @Value("${app.stripe.secret-key:}") String secretKey,
                             @Value("${app.stripe.price-maker-monthly:${app.stripe.price-hobi:}}") String priceMakerMonthly,
                             @Value("${app.stripe.price-maker-yearly:}")  String priceMakerYearly,
                             @Value("${app.stripe.price-pro-monthly:${app.stripe.price-pro:}}")    String priceProMonthly,
                             @Value("${app.stripe.price-pro-yearly:}")    String priceProYearly,
                             @Value("${app.stripe.price-team-monthly:}")  String priceTeamMonthly,
                             @Value("${app.stripe.price-team-yearly:}")   String priceTeamYearly,
                             @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.users = users;
        this.secretKey = secretKey;
        this.frontendUrl = frontendUrl;
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
        this.stripe = WebClient.builder()
                .baseUrl("https://api.stripe.com")
                .defaultHeader("Authorization", "Basic " + basic)
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        // tier + cycle → Stripe Price ID. Vana "hobi" → "maker" alias.
        this.priceMap = new HashMap<>();
        priceMap.put("maker:month", priceMakerMonthly);
        priceMap.put("maker:year",  priceMakerYearly);
        priceMap.put("hobi:month",  priceMakerMonthly); // legacy alias
        priceMap.put("hobi:year",   priceMakerYearly);
        priceMap.put("pro:month",   priceProMonthly);
        priceMap.put("pro:year",    priceProYearly);
        priceMap.put("team:month",  priceTeamMonthly);
        priceMap.put("team:year",   priceTeamYearly);
    }

    public record CheckoutReq(String tier, String cycle) {}

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutReq req) {
        if (secretKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "stripe_not_configured",
                    "message", "Stripe on seadistamisel. Tule peagi tagasi!"));
        }
        Long uid = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User u = users.findById(uid).orElseThrow();

        String tier = (req.tier() == null ? "maker" : req.tier()).toLowerCase(Locale.ROOT).trim();
        String cycle = (req.cycle() == null ? "month" : req.cycle()).toLowerCase(Locale.ROOT).trim();
        if (!cycle.equals("month") && !cycle.equals("year")) cycle = "month";

        String priceId = priceMap.get(tier + ":" + cycle);
        if (priceId == null || priceId.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "price_missing",
                    "message", "Hinnakiri seadistamisel (" + tier + "/" + cycle + ")."));
        }

        // Team-plaanil tahame mitu kohta — Stripe adjustable_quantity laseb kliendil
        // checkout-is kohti muuta. Miinimum 3, maksimum 50.
        Map<String, String> form = new HashMap<>();
        form.put("mode", "subscription");
        form.put("line_items[0][price]", priceId);
        form.put("line_items[0][quantity]", "team".equals(tier) ? "3" : "1");
        if ("team".equals(tier)) {
            form.put("line_items[0][adjustable_quantity][enabled]", "true");
            form.put("line_items[0][adjustable_quantity][minimum]", "3");
            form.put("line_items[0][adjustable_quantity][maximum]", "50");
        }
        form.put("client_reference_id", String.valueOf(u.getId()));
        form.put("customer_email", u.getEmail());
        form.put("metadata[tier]", tier);
        form.put("metadata[cycle]", cycle);
        // Aasta-plaanile automaatselt 14-päeva proovi­periood (müügi-trigger).
        if ("year".equals(cycle)) {
            form.put("subscription_data[trial_period_days]", "14");
        }
        form.put("allow_promotion_codes", "true");
        form.put("billing_address_collection", "required"); // e-arve jaoks
        form.put("tax_id_collection[enabled]", "true");     // B2B KMKR-numbri jaoks
        form.put("success_url", frontendUrl + "/#/billing?ok=1&tier=" + tier);
        form.put("cancel_url",  frontendUrl + "/#/pricing");

        Map<?, ?> res = stripe.post().uri("/v1/checkout/sessions")
                .bodyValue(form(form)).retrieve().bodyToMono(Map.class).block();

        Object url = res == null ? null : res.get("url");
        if (url == null) return ResponseEntity.status(502).body(Map.of("error", "stripe_failed"));
        return ResponseEntity.ok(Map.of("url", url.toString()));
    }

    private static String form(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
