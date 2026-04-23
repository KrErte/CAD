package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "printers")
public class Printer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 128)
    private String name;

    private String vendor;
    private String model;

    @Column(name = "build_volume_x_mm", nullable = false)
    private Integer buildVolumeXmm = 220;

    @Column(name = "build_volume_y_mm", nullable = false)
    private Integer buildVolumeYmm = 220;

    @Column(name = "build_volume_z_mm", nullable = false)
    private Integer buildVolumeZmm = 250;

    /** CSV, nt "PLA,PETG,ABS" */
    @Column(name = "supported_material_families", nullable = false, columnDefinition = "text")
    private String supportedMaterialFamilies = "PLA,PETG";

    @Column(name = "adapter_type", nullable = false, length = 32)
    private String adapterType = "MOCK"; // MOCK|BAMBU|MOONRAKER|OCTOPRINT|PRUSA_CONNECT

    @Column(name = "adapter_url", length = 512)
    private String adapterUrl;

    @Column(name = "adapter_api_key_encrypted", columnDefinition = "text")
    private String adapterApiKeyEncrypted;

    @Column(name = "hourly_rate_eur", precision = 8, scale = 2)
    private BigDecimal hourlyRateEur;

    @Column(nullable = false, length = 16)
    private String status = "OFFLINE"; // IDLE|PRINTING|PAUSED|ERROR|OFFLINE

    @Column(name = "current_job_id")
    private Long currentJobId;

    @Column(name = "progress_pct", nullable = false)
    private Integer progressPct = 0;

    @Column(name = "bed_temp_c", precision = 5, scale = 1)
    private BigDecimal bedTempC;

    @Column(name = "hotend_temp_c", precision = 5, scale = 1)
    private BigDecimal hotendTempC;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(columnDefinition = "text")
    private String notes;

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

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String v) {
        this.vendor = v;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String v) {
        this.model = v;
    }

    public Integer getBuildVolumeXmm() {
        return buildVolumeXmm;
    }

    public void setBuildVolumeXmm(Integer v) {
        this.buildVolumeXmm = v;
    }

    public Integer getBuildVolumeYmm() {
        return buildVolumeYmm;
    }

    public void setBuildVolumeYmm(Integer v) {
        this.buildVolumeYmm = v;
    }

    public Integer getBuildVolumeZmm() {
        return buildVolumeZmm;
    }

    public void setBuildVolumeZmm(Integer v) {
        this.buildVolumeZmm = v;
    }

    public String getSupportedMaterialFamilies() {
        return supportedMaterialFamilies;
    }

    public void setSupportedMaterialFamilies(String v) {
        this.supportedMaterialFamilies = v;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String v) {
        this.adapterType = v;
    }

    public String getAdapterUrl() {
        return adapterUrl;
    }

    public void setAdapterUrl(String v) {
        this.adapterUrl = v;
    }

    public String getAdapterApiKeyEncrypted() {
        return adapterApiKeyEncrypted;
    }

    public void setAdapterApiKeyEncrypted(String v) {
        this.adapterApiKeyEncrypted = v;
    }

    public BigDecimal getHourlyRateEur() {
        return hourlyRateEur;
    }

    public void setHourlyRateEur(BigDecimal v) {
        this.hourlyRateEur = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public Long getCurrentJobId() {
        return currentJobId;
    }

    public void setCurrentJobId(Long v) {
        this.currentJobId = v;
    }

    public Integer getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(Integer v) {
        this.progressPct = v;
    }

    public BigDecimal getBedTempC() {
        return bedTempC;
    }

    public void setBedTempC(BigDecimal v) {
        this.bedTempC = v;
    }

    public BigDecimal getHotendTempC() {
        return hotendTempC;
    }

    public void setHotendTempC(BigDecimal v) {
        this.hotendTempC = v;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant v) {
        this.lastHeartbeatAt = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
