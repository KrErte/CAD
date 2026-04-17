package ee.krerte.cad.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Creates a Stripe Checkout Session and returns the URL to redirect to.
 * User clicks "Upgrade" on frontend -> POST /api/billing/checkout { tier } -> redirect to Stripe.
 *
 * Requires app.stripe.secret-key + app.stripe.price-hobi + app.stripe.price-pro in env.
 * If keys are missing, returns 503 with helpful message (frontend shows it).
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final UserRepository users;
    private final String secretKey;
    private final String priceHobi;
    private final String pricePro;
    private final String frontendUrl;
    private final WebClient stripe;

    public BillingController(UserRepository users,
                             @Value("${app.stripe.secret-key:}") String secretKey,
                             @Value("${app.stripe.price-hobi:}") String priceHobi,
                             @Value("${app.stripe.price-pro:}") String pricePro,
                             @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.users = users;
        this.secretKey = secretKey;
        this.priceHobi = priceHobi;
        this.pricePro = pricePro;
        this.frontendUrl = frontendUrl;
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
        this.stripe = WebClient.builder()
                .baseUrl("https://api.stripe.com")
                .defaultHeader("Authorization", "Basic " + basic)
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }

    public record CheckoutReq(String tier) {}

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutReq req) {
        if (secretKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "stripe_not_configured",
                    "message", "Stripe on seadistamisel. Tule peagi tagasi!"));
        }
        Long uid = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User u = users.findById(uid).orElseThrow();

        String priceId = "pro".equalsIgnoreCase(req.tier()) ? pricePro : priceHobi;
        if (priceId.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "price_missing",
                    "message", "Hinnakiri seadistamisel."));
        }

        String body = form(Map.of(
                "mode", "subscription",
                "line_items[0][price]", priceId,
                "line_items[0][quantity]", "1",
                "client_reference_id", String.valueOf(u.getId()),
                "customer_email", u.getEmail(),
                "success_url", frontendUrl + "/#/billing?ok=1",
                "cancel_url", frontendUrl + "/#/pricing"
        ));

        Map<?, ?> res = stripe.post().uri("/v1/checkout/sessions")
                .bodyValue(body).retrieve().bodyToMono(Map.class).block();

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
