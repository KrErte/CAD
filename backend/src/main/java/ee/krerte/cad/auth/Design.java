package ee.krerte.cad.auth;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "designs")
public class Design {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String template;

    /** Raw JSON text of params — stored as jsonb on DB side. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "summary_et")
    private String summaryEt;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] stl;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long u) { this.userId = u; }
    public String getTemplate() { return template; }
    public void setTemplate(String t) { this.template = t; }
    public String getParams() { return params; }
    public void setParams(String p) { this.params = p; }
    public String getSummaryEt() { return summaryEt; }
    public void setSummaryEt(String s) { this.summaryEt = s; }
    public byte[] getStl() { return stl; }
    public void setStl(byte[] s) { this.stl = s; this.sizeBytes = s == null ? 0 : s.length; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
