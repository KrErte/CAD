package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quotes")
public class Quote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "quote_number", nullable = false, length = 32)
    private String quoteNumber;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT"; // DRAFT|SENT|ACCEPTED|REJECTED|EXPIRED

    @Column(name = "total_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEur = BigDecimal.ZERO;

    @Column(name = "subtotal_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalEur = BigDecimal.ZERO;

    @Column(name = "setup_fee_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal setupFeeEur = BigDecimal.ZERO;

    @Column(name = "margin_pct", nullable = false)
    private Integer marginPct = 40;

    @Column(name = "rush_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal rushMultiplier = BigDecimal.ONE;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "public_token", length = 64, unique = true)
    private String publicToken;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long v) {
        this.organizationId = v;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long v) {
        this.customerId = v;
    }

    public String getQuoteNumber() {
        return quoteNumber;
    }

    public void setQuoteNumber(String v) {
        this.quoteNumber = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public BigDecimal getTotalEur() {
        return totalEur;
    }

    public void setTotalEur(BigDecimal v) {
        this.totalEur = v;
    }

    public BigDecimal getSubtotalEur() {
        return subtotalEur;
    }

    public void setSubtotalEur(BigDecimal v) {
        this.subtotalEur = v;
    }

    public BigDecimal getSetupFeeEur() {
        return setupFeeEur;
    }

    public void setSetupFeeEur(BigDecimal v) {
        this.setupFeeEur = v;
    }

    public Integer getMarginPct() {
        return marginPct;
    }

    public void setMarginPct(Integer v) {
        this.marginPct = v;
    }

    public BigDecimal getRushMultiplier() {
        return rushMultiplier;
    }

    public void setRushMultiplier(BigDecimal v) {
        this.rushMultiplier = v;
    }

    public BigDecimal getDiscountPct() {
        return discountPct;
    }

    public void setDiscountPct(BigDecimal v) {
        this.discountPct = v;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant v) {
        this.validUntil = v;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String v) {
        this.publicToken = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long v) {
        this.createdByUserId = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant v) {
        this.sentAt = v;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant v) {
        this.acceptedAt = v;
    }
}
