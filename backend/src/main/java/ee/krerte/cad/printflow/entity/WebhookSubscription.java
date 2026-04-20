package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** CSV. e.g. "job.complete,quote.accepted,spool.low". */
    @Column(name = "event_types", nullable = false)
    private String eventTypes;

    @Column(name = "target_url", nullable = false, length = 1024)
    private String targetUrl;

    @Column(length = 128)
    private String secret;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long v) { this.organizationId = v; }
    public String getEventTypes() { return eventTypes; }
    public void setEventTypes(String v) { this.eventTypes = v; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String v) { this.targetUrl = v; }
    public String getSecret() { return secret; }
    public void setSecret(String v) { this.secret = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastFiredAt() { return lastFiredAt; }
    public void setLastFiredAt(Instant v) { this.lastFiredAt = v; }
    public Integer getLastStatusCode() { return lastStatusCode; }
    public void setLastStatusCode(Integer v) { this.lastStatusCode = v; }
}
