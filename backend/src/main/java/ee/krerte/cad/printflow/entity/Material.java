package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "materials")
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String family;  // PLA|PETG|ABS|PC|TPU|ASA|NYLON|RESIN|OTHER

    @Column(name = "price_per_kg_eur", nullable = false, precision = 8, scale = 2)
    private BigDecimal pricePerKgEur = new BigDecimal("25.00");

    @Column(name = "density_g_cm3", nullable = false, precision = 6, scale = 3)
    private BigDecimal densityGcm3 = new BigDecimal("1.240");

    @Column(name = "slicer_preset", length = 128)
    private String slicerPreset;

    @Column(name = "min_wall_mm", nullable = false, precision = 4, scale = 2)
    private BigDecimal minWallMm = new BigDecimal("1.20");

    @Column(name = "max_overhang_deg", nullable = false)
    private Integer maxOverhangDeg = 50;

    @Column(name = "setup_fee_eur", precision = 8, scale = 2)
    private BigDecimal setupFeeEur;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long v) { this.organizationId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getFamily() { return family; }
    public void setFamily(String v) { this.family = v; }
    public BigDecimal getPricePerKgEur() { return pricePerKgEur; }
    public void setPricePerKgEur(BigDecimal v) { this.pricePerKgEur = v; }
    public BigDecimal getDensityGcm3() { return densityGcm3; }
    public void setDensityGcm3(BigDecimal v) { this.densityGcm3 = v; }
    public String getSlicerPreset() { return slicerPreset; }
    public void setSlicerPreset(String v) { this.slicerPreset = v; }
    public BigDecimal getMinWallMm() { return minWallMm; }
    public void setMinWallMm(BigDecimal v) { this.minWallMm = v; }
    public Integer getMaxOverhangDeg() { return maxOverhangDeg; }
    public void setMaxOverhangDeg(Integer v) { this.maxOverhangDeg = v; }
    public BigDecimal getSetupFeeEur() { return setupFeeEur; }
    public void setSetupFeeEur(BigDecimal v) { this.setupFeeEur = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }
    public Instant getCreatedAt() { return createdAt; }
}
