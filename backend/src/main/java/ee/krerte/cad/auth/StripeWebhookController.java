package ee.krerte.cad.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * LEGACY Stripe webhook receiver — superseded by StripeController + StripeService.
 * Kept as fallback but disabled (@Deprecated + commented out @RestController).
 * Remove after verifying StripeService handles all events correctly in production.
 */
@Deprecated
// @RestController — DISABLED: StripeController now handles /api/stripe/webhook
// @RequestMapping("/api/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final UserRepository users;
    private final String webhookSecret;
    private final ObjectMapper mapper = new ObjectMapper();

    public StripeWebhookController(UserRepository users,
                                   @Value("${app.stripe.webhook-secret:}") String webhookSecret) {
        this.users = users;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<?> webhook(@RequestHeader(value = "Stripe-Signature", required = false) String sig,
                                     @RequestBody String payload) {
        if (!webhookSecret.isBlank() && !verifySignature(sig, payload)) {
            return ResponseEntity.status(400).body("bad signature");
        }
        try {
            JsonNode evt = mapper.readTree(payload);
            String type = evt.path("type").asText();
            JsonNode obj = evt.path("data").path("object");
            switch (type) {
                case "checkout.session.completed" -> onCheckout(obj);
                case "invoice.paid" -> onInvoicePaid(obj);
                case "customer.subscription.deleted" -> onSubCancelled(obj);
                default -> log.debug("Ignored stripe event: {}", type);
            }
        } catch (Exception e) {
            log.error("Stripe webhook error", e);
            return ResponseEntity.status(500).body("error");
        }
        return ResponseEntity.ok("ok");
    }

    private void onCheckout(JsonNode obj) {
        String customerRef = obj.path("client_reference_id").asText(null);
        String customerId = obj.path("customer").asText(null);
        String subId = obj.path("subscription").asText(null);
        if (customerRef == null) return;
        users.findById(Long.valueOf(customerRef)).ifPresent(u -> {
            u.setPlan(User.Plan.PRO);
            u.setStripeCustomerId(customerId);
            u.setStripeSubscriptionId(subId);
            u.setPlanActiveUntil(Instant.now().plusSeconds(35 * 24 * 3600L));
            users.save(u);
        });
    }

    private void onInvoicePaid(JsonNode obj) {
        String customerId = obj.path("customer").asText(null);
        if (customerId == null) return;
        users.findByStripeCustomerId(customerId).ifPresent(u -> {
            u.setPlan(User.Plan.PRO);
            u.setPlanActiveUntil(Instant.now().plusSeconds(35 * 24 * 3600L));
            users.save(u);
        });
    }

    private void onSubCancelled(JsonNode obj) {
        String customerId = obj.path("customer").asText(null);
        if (customerId == null) return;
        users.findByStripeCustomerId(customerId).ifPresent(u -> {
            u.setPlan(User.Plan.FREE);
            users.save(u);
        });
    }

    /** Minimal Stripe signature verification (v1 scheme). */
    private boolean verifySignature(String header, String payload) {
        if (header == null) return false;
        try {
            String t = null, v1 = null;
            for (String part : header.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                if (kv[0].equals("t")) t = kv[1];
                else if (kv[0].equals("v1")) v1 = kv[1];
            }
            if (t == null || v1 == null) return false;
            String signed = t + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
            return hex.equals(v1);
        } catch (Exception e) { return false; }
    }
}
