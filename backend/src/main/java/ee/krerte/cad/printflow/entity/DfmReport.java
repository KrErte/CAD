package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dfm_reports")
public class DfmReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes = 0;

    @Column(name = "bbox_x_mm", precision = 8, scale = 2)
    private BigDecimal bboxXmm;

    @Column(name = "bbox_y_mm", precision = 8, scale = 2)
    private BigDecimal bboxYmm;

    @Column(name = "bbox_z_mm", precision = 8, scale = 2)
    private BigDecimal bboxZmm;

    @Column(name = "volume_cm3", precision = 10, scale = 3)
    private BigDecimal volumeCm3;

    private Integer triangles;

    @Column(name = "is_watertight")
    private Boolean isWatertight;

    @Column(name = "self_intersections")
    private Integer selfIntersections;

    @Column(name = "min_wall_mm", precision = 5, scale = 2)
    private BigDecimal minWallMm;

    @Column(name = "overhang_area_cm2", precision = 10, scale = 3)
    private BigDecimal overhangAreaCm2;

    @Column(name = "overhang_pct", precision = 5, scale = 2)
    private BigDecimal overhangPct;

    @Column(name = "thin_features_count")
    private Integer thinFeaturesCount;

    /**
     * JSON text (stored as jsonb). We keep it as String to avoid an optional Hibernate-JSON mapping
     * dependency; the service layer (re)serializes when writing.
     */
    @Column(columnDefinition = "jsonb")
    private String issues;

    @Column(nullable = false, length = 8)
    private String severity = "OK"; // OK|WARN|BLOCK

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long v) {
        this.organizationId = v;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String v) {
        this.fileName = v;
    }

    public Integer getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Integer v) {
        this.sizeBytes = v;
    }

    public BigDecimal getBboxXmm() {
        return bboxXmm;
    }

    public void setBboxXmm(BigDecimal v) {
        this.bboxXmm = v;
    }

    public BigDecimal getBboxYmm() {
        return bboxYmm;
    }

    public void setBboxYmm(BigDecimal v) {
        this.bboxYmm = v;
    }

    public BigDecimal getBboxZmm() {
        return bboxZmm;
    }

    public void setBboxZmm(BigDecimal v) {
        this.bboxZmm = v;
    }

    public BigDecimal getVolumeCm3() {
        return volumeCm3;
    }

    public void setVolumeCm3(BigDecimal v) {
        this.volumeCm3 = v;
    }

    public Integer getTriangles() {
        return triangles;
    }

    public void setTriangles(Integer v) {
        this.triangles = v;
    }

    public Boolean getIsWatertight() {
        return isWatertight;
    }

    public void setIsWatertight(Boolean v) {
        this.isWatertight = v;
    }

    public Integer getSelfIntersections() {
        return selfIntersections;
    }

    public void setSelfIntersections(Integer v) {
        this.selfIntersections = v;
    }

    public BigDecimal getMinWallMm() {
        return minWallMm;
    }

    public void setMinWallMm(BigDecimal v) {
        this.minWallMm = v;
    }

    public BigDecimal getOverhangAreaCm2() {
        return overhangAreaCm2;
    }

    public void setOverhangAreaCm2(BigDecimal v) {
        this.overhangAreaCm2 = v;
    }

    public BigDecimal getOverhangPct() {
        return overhangPct;
    }

    public void setOverhangPct(BigDecimal v) {
        this.overhangPct = v;
    }

    public Integer getThinFeaturesCount() {
        return thinFeaturesCount;
    }

    public void setThinFeaturesCount(Integer v) {
        this.thinFeaturesCount = v;
    }

    public String getIssues() {
        return issues;
    }

    public void setIssues(String v) {
        this.issues = v;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String v) {
        this.severity = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
