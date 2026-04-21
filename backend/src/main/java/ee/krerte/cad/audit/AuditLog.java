package ee.krerte.cad.audit;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Immutable audit log record. Vastav tabel loodi Flyway V6'ga.
 *
 * <p><b>Immutable</b>: meil pole update/delete meetodeid. Postgres'is
 * saame lisaks trigger'i, mis blokkerib UPDATE/DELETE iganes, keda
 * rakendus kasutab — database-level forensic-read-only guarantee.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_ip", columnDefinition = "inet")
    private String actorIp;

    @Column(name = "actor_ua", columnDefinition = "text")
    private String actorUa;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, length = 16)
    private String outcome;   // SUCCESS | FAILURE | DENIED

    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long v) { this.actorUserId = v; }
    public String getActorIp() { return actorIp; }
    public void setActorIp(String v) { this.actorIp = v; }
    public String getActorUa() { return actorUa; }
    public void setActorUa(String v) { this.actorUa = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String v) { this.targetType = v; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long v) { this.targetId = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String v) { this.detailsJson = v; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }
    public Instant getCreatedAt() { return createdAt; }
}
