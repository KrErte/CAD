package ee.krerte.cad.gallery;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "design_versions")
public class DesignVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_id", nullable = false)
    private Long designId;

    @Column(nullable = false)
    private int version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "summary_et")
    private String summaryEt;

    @Column(columnDefinition = "bytea")
    private byte[] stl;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getDesignId() { return designId; }
    public void setDesignId(Long d) { this.designId = d; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
    public String getParams() { return params; }
    public void setParams(String p) { this.params = p; }
    public String getSummaryEt() { return summaryEt; }
    public void setSummaryEt(String s) { this.summaryEt = s; }
    public byte[] getStl() { return stl; }
    public void setStl(byte[] s) { this.stl = s; this.sizeBytes = s == null ? 0 : s.length; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
