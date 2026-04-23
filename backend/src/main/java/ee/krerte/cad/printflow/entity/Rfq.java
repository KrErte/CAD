package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "rfqs")
public class Rfq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "quantity_hint")
    private Integer quantityHint;

    @Column(name = "material_hint", length = 64)
    private String materialHint;

    private LocalDate deadline;

    @Column(columnDefinition = "text")
    private String attachments; // JSON

    @Column(nullable = false, length = 16)
    private String status = "NEW";

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "quote_id")
    private Long quoteId;

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

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String v) {
        this.contactName = v;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String v) {
        this.contactEmail = v;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String v) {
        this.contactPhone = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public Integer getQuantityHint() {
        return quantityHint;
    }

    public void setQuantityHint(Integer v) {
        this.quantityHint = v;
    }

    public String getMaterialHint() {
        return materialHint;
    }

    public void setMaterialHint(String v) {
        this.materialHint = v;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate v) {
        this.deadline = v;
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String v) {
        this.attachments = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public Long getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(Long v) {
        this.assignedToUserId = v;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public void setQuoteId(Long v) {
        this.quoteId = v;
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
