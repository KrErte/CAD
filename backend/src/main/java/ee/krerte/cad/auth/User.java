package ee.krerte.cad.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    /** Google sub claim — stable user id from Google OAuth */
    @Column(unique = true)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private Instant planActiveUntil;

    private String passwordHash;

    private Instant createdAt = Instant.now();

    public enum Plan { FREE, PRO, BUSINESS }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String s) { this.stripeCustomerId = s; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String s) { this.stripeSubscriptionId = s; }
    public Instant getPlanActiveUntil() { return planActiveUntil; }
    public void setPlanActiveUntil(Instant i) { this.planActiveUntil = i; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
