package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "build_plates")
public class BuildPlate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "printer_id")
    private Long printerId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "plate_x_mm")
    private Integer plateXmm;

    @Column(name = "plate_y_mm")
    private Integer plateYmm;

    @Column(name = "nesting_data", columnDefinition = "text")
    private String nestingData;   // JSON

    @Column(nullable = false, length = 16)
    private String status = "PLANNED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "printed_at")
    private Instant printedAt;

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long v) { this.organizationId = v; }
    public Long getPrinterId() { return printerId; }
    public void setPrinterId(Long v) { this.printerId = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public Integer getPlateXmm() { return plateXmm; }
    public void setPlateXmm(Integer v) { this.plateXmm = v; }
    public Integer getPlateYmm() { return plateYmm; }
    public void setPlateYmm(Integer v) { this.plateYmm = v; }
    public String getNestingData() { return nestingData; }
    public void setNestingData(String v) { this.nestingData = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPrintedAt() { return printedAt; }
    public void setPrintedAt(Instant v) { this.printedAt = v; }
}
