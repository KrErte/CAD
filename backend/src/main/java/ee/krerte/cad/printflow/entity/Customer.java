package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 8)
    private String kind = "B2C"; // B2B | B2C

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    @Column(name = "vat_id")
    private String vatId;

    @Column(name = "billing_address", columnDefinition = "text")
    private String billingAddress;

    @Column(name = "shipping_address", columnDefinition = "text")
    private String shippingAddress;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "default_margin_pct")
    private Integer defaultMarginPct;

    @Column(name = "linked_user_id")
    private Long linkedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long v) {
        this.organizationId = v;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String v) {
        this.kind = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        this.email = v;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        this.phone = v;
    }

    public String getVatId() {
        return vatId;
    }

    public void setVatId(String v) {
        this.vatId = v;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String v) {
        this.billingAddress = v;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String v) {
        this.shippingAddress = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Integer getDefaultMarginPct() {
        return defaultMarginPct;
    }

    public void setDefaultMarginPct(Integer v) {
        this.defaultMarginPct = v;
    }

    public Long getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(Long v) {
        this.linkedUserId = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant v) {
        this.updatedAt = v;
    }
}
