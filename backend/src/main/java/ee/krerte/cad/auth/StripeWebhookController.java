package ee.krerte.cad.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.krerte.cad.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Stripe webhook receiver. Scaffold — set app.stripe.webhook-secret to your whsec_ value.
 *
 * <p>Handled events:
 * <ul>
 *   <li><b>checkout.session.completed</b>  → mark user PRO + save subscription id</li>
 *   <li><b>invoice.paid</b>                → extend planActiveUntil</li>
 *   <li><b>customer.subscription.deleted</b> → downgrade to FREE</li>
 *   <li><b>charge.refunded</b>             → record refund + audit (chargeback-hygiene)</li>
 *   <li><b>charge.dispute.created</b>      → record dispute, FLAG for ops (evidence due)</li>
 *   <li><b>charge.dispute.closed</b>       → update dispute outcome (won/lost)</li>
 * </ul>
 *
 * <p>In frontend you create a Checkout Session with client_reference_id = user.id.
 *
 * <p><b>Idempotency</b>: Kõik kaks DB INSERT'i on ON CONFLICT (stripe_*_id) DO UPDATE,
 * nii et Stripe'i retry (sama event_id, sama refund_id) ei loo duplikaate.
 */
@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final String webhookSecret;
    private final ObjectMapper mapper = new ObjectMapper();

    public StripeWebhookController(UserRepository users,
                                   JdbcTemplate jdbc,
                                   AuditService audit,
                                   @Value("${app.stripe.webhook-secret:}") String webhookSecret) {
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
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
                case "checkout.session.completed"   -> onCheckout(obj);
                case "invoice.paid"                 -> onInvoicePaid(obj);
                case "customer.subscription.deleted" -> onSubCancelled(obj);
                case "charge.refunded"              -> onRefund(obj);
                case "charge.dispute.created"       -> onDisputeCreated(obj);
                case "charge.dispute.closed",
                     "charge.dispute.updated"       -> onDisputeClosed(obj);
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

    // ─────────────────────────────────────────────────────────────────
    // Refunds / disputes — kaasas V7 migratsiooniga
    // ─────────────────────────────────────────────────────────────────

    /**
     * charge.refunded: Stripe tagastas raha (full või partial). Logime,
     * et toe tiim nägeks history't + audit_log saab kirje.
     */
    private void onRefund(JsonNode obj) {
        // "obj" siin on Charge; refund info on nested array refunds.data
        String chargeId = obj.path("id").asText(null);
        String customerId = obj.path("customer").asText(null);
        String currency = obj.path("currency").asText("eur");
        Long userId = resolveUserId(customerId);

        JsonNode refunds = obj.path("refunds").path("data");
        if (!refunds.isArray() || refunds.isEmpty()) {
            log.debug("charge.refunded without refunds array — charge={}", chargeId);
            return;
        }

        for (JsonNode r : refunds) {
            String refundId = r.path("id").asText();
            long amountCents = r.path("amount").asLong(0);
            String reason = r.path("reason").asText(null);
            String status = r.path("status").asText("succeeded");

            jdbc.update(
                "INSERT INTO stripe_refunds " +
                "(stripe_refund_id, stripe_payment_id, user_id, amount_cents, currency, reason, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (stripe_refund_id) DO UPDATE SET status = EXCLUDED.status",
                refundId, chargeId, userId, amountCents, currency, reason, status);

            audit.record("STRIPE_REFUND", "charge", userId, "SUCCESS",
                details("refund_id", refundId, "charge_id", chargeId,
                        "amount_cents", amountCents, "currency", currency,
                        "reason", reason, "status", status));
        }
    }

    /**
     * charge.dispute.created: kaardi-omanik vaidlustas makse. Meil on
     * tavaliselt ~7-21 päeva aega evidence'i esitada.
     */
    private void onDisputeCreated(JsonNode obj) {
        String disputeId = obj.path("id").asText();
        String chargeId = obj.path("charge").asText(null);
        long amountCents = obj.path("amount").asLong(0);
        String currency = obj.path("currency").asText("eur");
        String reason = obj.path("reason").asText(null);
        String status = obj.path("status").asText("warning_needs_response");
        Long dueBy = obj.path("evidence_details").path("due_by").asLong(0);
        Timestamp evidenceDue = dueBy > 0 ? new Timestamp(dueBy * 1000L) : null;

        String customerId = obj.path("customer").asText(null);  // alati null, fallback:
        Long userId = customerId != null ? resolveUserId(customerId)
                                         : resolveUserByCharge(chargeId);

        jdbc.update(
            "INSERT INTO stripe_disputes " +
            "(stripe_dispute_id, stripe_charge_id, user_id, amount_cents, currency, " +
            " reason, status, evidence_due_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (stripe_dispute_id) DO UPDATE SET " +
            "  status = EXCLUDED.status, evidence_due_by = EXCLUDED.evidence_due_by, " +
            "  updated_at = NOW()",
            disputeId, chargeId, userId, amountCents, currency, reason, status, evidenceDue);

        audit.record("STRIPE_DISPUTE_OPENED", "charge", userId, "WARNING",
            details("dispute_id", disputeId, "charge_id", chargeId,
                    "amount_cents", amountCents, "reason", reason,
                    "evidence_due_by", evidenceDue == null ? null : evidenceDue.toString()));

        log.warn("Stripe dispute OPENED — dispute={} charge={} amount={} {} reason={} due={}",
            disputeId, chargeId, amountCents, currency, reason, evidenceDue);
    }

    /**
     * charge.dispute.closed / updated: final outcome (won / lost / warning_closed).
     * Kui "lost", siis loogiliselt peaksime user'ilt PRO ära võtma (kui ta seda
     * disputed'i maksis) — tee seda tavaliselt manuaalselt + notify ops.
     */
    private void onDisputeClosed(JsonNode obj) {
        String disputeId = obj.path("id").asText();
        String status = obj.path("status").asText();

        int updated = jdbc.update(
            "UPDATE stripe_disputes SET status = ?, updated_at = NOW() " +
            "WHERE stripe_dispute_id = ?", status, disputeId);

        if (updated == 0) {
            log.warn("Dispute close for unknown dispute_id={} status={}", disputeId, status);
            return;
        }

        audit.record("STRIPE_DISPUTE_CLOSED", "charge", null,
            "lost".equals(status) ? "FAILURE" : "SUCCESS",
            details("dispute_id", disputeId, "status", status));

        if ("lost".equals(status)) {
            log.error("Stripe dispute LOST — dispute={}. Review manually whether to revoke PRO.",
                disputeId);
        }
    }

    /**
     * Null-safe map builder (Map.of ei luba null väärtusi, aga Stripe'i payload'is
     * on palju optional välju — me ei taha NPE'd audit-kirjutamise tõttu).
     */
    private static Map<String, Object> details(Object... kv) {
        var m = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private Long resolveUserId(String customerId) {
        if (customerId == null) return null;
        return users.findByStripeCustomerId(customerId).map(User::getId).orElse(null);
    }

    /** Otsi user'i charge'i kaudu kui customer_id pole event'is kaasas. */
    private Long resolveUserByCharge(String chargeId) {
        if (chargeId == null) return null;
        try {
            return jdbc.queryForObject(
                "SELECT user_id FROM stripe_refunds WHERE stripe_payment_id = ? LIMIT 1",
                Long.class, chargeId);
        } catch (Exception e) {
            return null;
        }
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
