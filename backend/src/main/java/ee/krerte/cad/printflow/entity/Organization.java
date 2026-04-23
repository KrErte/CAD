package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, length = 32)
    private String plan = "SOLO";

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "hourly_rate_eur", nullable = false, precision = 8, scale = 2)
    private BigDecimal hourlyRateEur = new BigDecimal("2.50");

    @Column(name = "default_margin_pct", nullable = false)
    private Integer defaultMarginPct = 40;

    @Column(name = "default_setup_fee_eur", nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultSetupFeeEur = new BigDecimal("3.00");

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ── getters / setters ────────────────────────────────────────────
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long u) {
        this.ownerUserId = u;
    }

    public BigDecimal getHourlyRateEur() {
        return hourlyRateEur;
    }

    public void setHourlyRateEur(BigDecimal v) {
        this.hourlyRateEur = v;
    }

    public Integer getDefaultMarginPct() {
        return defaultMarginPct;
    }

    public void setDefaultMarginPct(Integer v) {
        this.defaultMarginPct = v;
    }

    public BigDecimal getDefaultSetupFeeEur() {
        return defaultSetupFeeEur;
    }

    public void setDefaultSetupFeeEur(BigDecimal v) {
        this.defaultSetupFeeEur = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
