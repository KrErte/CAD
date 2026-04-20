package ee.krerte.cad.auth;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Stripe payment endpoints.
 *
 * <ul>
 *   <li>POST /api/stripe/checkout — creates Stripe Checkout session, returns URL</li>
 *   <li>POST /api/stripe/webhook — Stripe webhook receiver (signature verified)</li>
 *   <li>GET  /api/stripe/subscription — get current user's subscription status</li>
 *   <li>POST /api/stripe/portal — create Stripe Billing Portal session</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    private static final Logger log = LoggerFactory.getLogger(StripeController.class);
    private final StripeService stripeService;

    public StripeController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    // ───────── Checkout ─────────

    public record CheckoutRequest(String tier) {}

    /**
     * POST /api/stripe/checkout
     * Creates a Stripe Checkout session for the given tier (pro/business).
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest req) {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "stripe_not_configured",
                    "message", "Stripe on seadistamisel. Tule peagi tagasi!"));
        }

        Long userId = currentUserId();
        String tier = req.tier() == null ? "pro" : req.tier().toLowerCase().trim();
        String priceId = stripeService.getPriceId(tier);

        if (priceId == null || priceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_tier",
                    "message", "Tundmatu plaan: " + tier + ". Valige 'pro' või 'business'."));
        }

        try {
            String url = stripeService.createCheckoutSession(userId, priceId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (StripeException e) {
            log.error("Stripe checkout failed for user {}", userId, e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "stripe_error", "message", e.getMessage()));
        }
    }

    // ───────── Webhook ─────────

    /**
     * POST /api/stripe/webhook
     * Stripe sends events here. Signature is verified by StripeService.
     */
    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<?> webhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String sig,
            @RequestBody String payload) {
        try {
            stripeService.handleWebhook(payload, sig);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            return ResponseEntity.status(400).body("bad signature");
        } catch (Exception e) {
            log.error("Stripe webhook processing error", e);
            return ResponseEntity.status(500).body("error");
        }
    }

    // ───────── Subscription Status ─────────

    /**
     * GET /api/stripe/subscription
     * Returns the current user's subscription details.
     */
    @GetMapping("/subscription")
    public ResponseEntity<?> subscription() {
        Long userId = currentUserId();
        StripeService.SubscriptionStatus status = stripeService.getSubscription(userId);
        return ResponseEntity.ok(Map.of(
                "plan", status.plan().name(),
                "status", status.status(),
                "currentPeriodEnd", status.currentPeriodEnd() != null ? status.currentPeriodEnd().toString() : "",
                "modelCount", status.modelCount(),
                "modelLimit", status.modelLimit(),
                "hasSubscription", status.stripeSubscriptionId() != null
        ));
    }

    // ───────── Billing Portal ─────────

    /**
     * POST /api/stripe/portal
     * Creates a Stripe Billing Portal session for the user to manage their subscription.
     */
    @PostMapping("/portal")
    public ResponseEntity<?> portal() {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "stripe_not_configured",
                    "message", "Stripe on seadistamisel."));
        }

        Long userId = currentUserId();
        try {
            String url = stripeService.createPortalSession(userId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "no_subscription",
                    "message", "Sul pole aktiivset tellimust."));
        } catch (StripeException e) {
            log.error("Stripe portal creation failed for user {}", userId, e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "stripe_error", "message", e.getMessage()));
        }
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
