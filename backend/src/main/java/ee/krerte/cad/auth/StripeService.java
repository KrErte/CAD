package ee.krerte.cad.auth;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Centralised Stripe integration service.
 *
 * <p>Hinnastruktuur:
 * <ul>
 *   <li>Free — 0 €, 3 mudelit/kuu</li>
 *   <li>Pro — 14.99 €/kuu, 50 mudelit/kuu</li>
 *   <li>Business — 49.99 €/kuu, 200 mudelit/kuu + API ligipääs</li>
 * </ul>
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final UserRepository users;
    private final UserSubscriptionRepository subscriptions;

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.stripe.price-pro:}")
    private String priceProId;

    @Value("${app.stripe.price-business:}")
    private String priceBusinessId;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    private static final Map<User.Plan, Integer> PLAN_LIMITS = Map.of(
            User.Plan.FREE, 3,
            User.Plan.PRO, 50,
            User.Plan.BUSINESS, 200
    );

    public StripeService(UserRepository users, UserSubscriptionRepository subscriptions) {
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @PostConstruct
    void init() {
        if (!secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
            log.info("Stripe API key configured");
        } else {
            log.warn("Stripe API key NOT configured — payments disabled");
        }
    }

    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    // ───────── Checkout ─────────

    /**
     * Creates a Stripe Checkout Session for upgrading to Pro or Business.
     * Returns the checkout URL to redirect the user to.
     */
    public String createCheckoutSession(Long userId, String priceId) throws StripeException {
        User user = users.findById(userId).orElseThrow(
                () -> new IllegalArgumentException("User not found: " + userId));

        // Reuse existing Stripe customer or create a new one
        String customerId = getOrCreateCustomerId(user);

        // Determine tier from price ID
        String tier = priceId.equals(priceProId) ? "pro" : "business";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setClientReferenceId(String.valueOf(userId))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .putMetadata("tier", tier)
                .putMetadata("user_id", String.valueOf(userId))
                .setSuccessUrl(frontendUrl + "/#/billing?ok=1&tier=" + tier)
                .setCancelUrl(frontendUrl + "/#/pricing")
                .setAllowPromotionCodes(true)
                .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                .build();

        com.stripe.model.checkout.Session session =
                com.stripe.model.checkout.Session.create(params);
        return session.getUrl();
    }

    /**
     * Returns the Stripe price ID for a given tier name.
     */
    public String getPriceId(String tier) {
        return switch (tier.toLowerCase()) {
            case "pro" -> priceProId;
            case "business" -> priceBusinessId;
            default -> null;
        };
    }

    // ───────── Billing Portal ─────────

    /**
     * Creates a Stripe Billing Portal session for managing subscription.
     */
    public String createPortalSession(Long userId) throws StripeException {
        User user = users.findById(userId).orElseThrow();
        UserSubscription sub = subscriptions.findByUserId(userId).orElse(null);
        String customerId = sub != null ? sub.getStripeCustomerId() : user.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("No Stripe customer for user " + userId);
        }

        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(frontendUrl + "/#/billing")
                        .build();

        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    // ───────── Subscription Status ─────────

    public record SubscriptionStatus(
            User.Plan plan, String status, Instant currentPeriodEnd,
            int modelCount, int modelLimit, String stripeSubscriptionId) {}

    /**
     * Returns the current subscription status for a user.
     */
    public SubscriptionStatus getSubscription(Long userId) {
        Optional<UserSubscription> subOpt = subscriptions.findByUserId(userId);
        if (subOpt.isPresent()) {
            UserSubscription sub = subOpt.get();
            return new SubscriptionStatus(
                    sub.getPlan(), sub.getStatus(), sub.getCurrentPeriodEnd(),
                    sub.getModelCount(), sub.getModelLimit(), sub.getStripeSubscriptionId());
        }
        // Fallback to User entity
        User user = users.findById(userId).orElseThrow();
        int limit = PLAN_LIMITS.getOrDefault(user.getPlan(), 3);
        return new SubscriptionStatus(user.getPlan(), "active", user.getPlanActiveUntil(),
                0, limit, user.getStripeSubscriptionId());
    }

    public static int getLimitForPlan(User.Plan plan) {
        return PLAN_LIMITS.getOrDefault(plan, 3);
    }

    // ───────── Webhook Handling ─────────

    /**
     * Verifies the Stripe webhook signature and processes the event.
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        Event event;
        if (!webhookSecret.isBlank()) {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } else {
            // Dev mode — no signature verification
            try {
                event = Event.GSON.fromJson(payload, Event.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse webhook payload", e);
            }
        }

        String type = event.getType();
        log.info("Stripe webhook received: {}", type);

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isEmpty()) {
            log.warn("Could not deserialize webhook event object for: {}", type);
            return;
        }
        StripeObject stripeObject = deserializer.getObject().get();

        switch (type) {
            case "checkout.session.completed" -> handleCheckoutCompleted(stripeObject);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(stripeObject);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(stripeObject);
            case "invoice.paid" -> handleInvoicePaid(stripeObject);
            default -> log.debug("Unhandled Stripe event: {}", type);
        }
    }

    private void handleCheckoutCompleted(StripeObject obj) {
        com.stripe.model.checkout.Session session = (com.stripe.model.checkout.Session) obj;
        String customerRef = session.getClientReferenceId();
        String customerId = session.getCustomer();
        String subId = session.getSubscription();
        Map<String, String> metadata = session.getMetadata();
        String tier = metadata != null ? metadata.getOrDefault("tier", "pro") : "pro";

        if (customerRef == null) {
            log.warn("checkout.session.completed without client_reference_id");
            return;
        }

        Long userId = Long.valueOf(customerRef);
        User.Plan plan = "business".equalsIgnoreCase(tier) ? User.Plan.BUSINESS : User.Plan.PRO;
        int limit = getLimitForPlan(plan);

        // Update User entity
        users.findById(userId).ifPresent(u -> {
            u.setPlan(plan);
            u.setStripeCustomerId(customerId);
            u.setStripeSubscriptionId(subId);
            u.setPlanActiveUntil(Instant.now().plusSeconds(35 * 24 * 3600L));
            users.save(u);
        });

        // Upsert UserSubscription
        UserSubscription sub = subscriptions.findByUserId(userId)
                .orElseGet(() -> {
                    UserSubscription s = new UserSubscription();
                    s.setUserId(userId);
                    return s;
                });
        sub.setStripeCustomerId(customerId);
        sub.setStripeSubscriptionId(subId);
        sub.setPlan(plan);
        sub.setStatus("active");
        sub.setModelLimit(limit);
        sub.setCurrentPeriodEnd(Instant.now().plusSeconds(35 * 24 * 3600L));
        sub.setUpdatedAt(Instant.now());
        subscriptions.save(sub);

        log.info("User {} upgraded to {} (sub={})", userId, plan, subId);
    }

    private void handleSubscriptionUpdated(StripeObject obj) {
        Subscription subscription = (Subscription) obj;
        String customerId = subscription.getCustomer();
        String status = subscription.getStatus();

        subscriptions.findByStripeCustomerId(customerId).ifPresent(sub -> {
            sub.setStatus(status);
            if (subscription.getCurrentPeriodEnd() != null) {
                sub.setCurrentPeriodEnd(Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()));
            }
            sub.setUpdatedAt(Instant.now());
            subscriptions.save(sub);

            // Sync plan status to User entity
            users.findByStripeCustomerId(customerId).ifPresent(u -> {
                if ("active".equals(status) || "trialing".equals(status)) {
                    // Plan stays as-is
                } else if ("canceled".equals(status) || "unpaid".equals(status)) {
                    u.setPlan(User.Plan.FREE);
                    users.save(u);
                }
            });
        });
    }

    private void handleSubscriptionDeleted(StripeObject obj) {
        Subscription subscription = (Subscription) obj;
        String customerId = subscription.getCustomer();

        subscriptions.findByStripeCustomerId(customerId).ifPresent(sub -> {
            sub.setPlan(User.Plan.FREE);
            sub.setStatus("canceled");
            sub.setModelLimit(getLimitForPlan(User.Plan.FREE));
            sub.setUpdatedAt(Instant.now());
            subscriptions.save(sub);
        });

        users.findByStripeCustomerId(customerId).ifPresent(u -> {
            u.setPlan(User.Plan.FREE);
            users.save(u);
        });

        log.info("Subscription canceled for customer {}", customerId);
    }

    private void handleInvoicePaid(StripeObject obj) {
        Invoice invoice = (Invoice) obj;
        String customerId = invoice.getCustomer();

        users.findByStripeCustomerId(customerId).ifPresent(u -> {
            u.setPlanActiveUntil(Instant.now().plusSeconds(35 * 24 * 3600L));
            users.save(u);
        });

        subscriptions.findByStripeCustomerId(customerId).ifPresent(sub -> {
            sub.setCurrentPeriodEnd(Instant.now().plusSeconds(35 * 24 * 3600L));
            sub.setModelCount(0); // Reset monthly count on new billing period
            sub.setUpdatedAt(Instant.now());
            subscriptions.save(sub);
        });
    }

    // ───────── Helpers ─────────

    private String getOrCreateCustomerId(User user) throws StripeException {
        // Check UserSubscription first
        Optional<UserSubscription> subOpt = subscriptions.findByUserId(user.getId());
        if (subOpt.isPresent() && subOpt.get().getStripeCustomerId() != null) {
            return subOpt.get().getStripeCustomerId();
        }
        // Check User entity
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        // Create new Stripe customer
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getName())
                .putMetadata("user_id", String.valueOf(user.getId()))
                .build();
        Customer customer = Customer.create(params);
        user.setStripeCustomerId(customer.getId());
        users.save(user);
        return customer.getId();
    }
}
