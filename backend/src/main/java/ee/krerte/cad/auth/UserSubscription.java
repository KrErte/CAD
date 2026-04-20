package ee.krerte.cad.auth;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks Stripe subscription details per user.
 * Separated from User to keep subscription lifecycle data clean.
 */
@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.Plan plan = User.Plan.FREE;

    /** Stripe subscription status: active, past_due, canceled, trialing, etc. */
    @Column(nullable = false, length = 32)
    private String status = "active";

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    /** Models generated this month (denormalized for fast access). */
    @Column(name = "model_count", nullable = false)
    private int modelCount = 0;

    /** Monthly model limit for this plan. */
    @Column(name = "model_limit", nullable = false)
    private int modelLimit = 3;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String s) { this.stripeCustomerId = s; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String s) { this.stripeSubscriptionId = s; }
    public User.Plan getPlan() { return plan; }
    public void setPlan(User.Plan plan) { this.plan = plan; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant i) { this.currentPeriodEnd = i; }
    public int getModelCount() { return modelCount; }
    public void setModelCount(int c) { this.modelCount = c; }
    public int getModelLimit() { return modelLimit; }
    public void setModelLimit(int l) { this.modelLimit = l; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant i) { this.updatedAt = i; }
}
