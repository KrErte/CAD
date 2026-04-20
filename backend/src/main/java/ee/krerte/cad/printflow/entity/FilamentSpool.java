package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "filament_spools")
public class FilamentSpool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(length = 64)
    private String color;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "mass_initial_g", nullable = false)
    private Integer massInitialG = 1000;

    @Column(name = "mass_remaining_g", nullable = false)
    private Integer massRemainingG = 1000;

    @Column(name = "serial_barcode", length = 64)
    private String serialBarcode;

    private String vendor;

    @Column(name = "lot_code", length = 64)
    private String lotCode;

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "assigned_printer_id")
    private Long assignedPrinterId;

    @Column(nullable = false, length = 16)
    private String status = "FULL";   // FULL|PARTIAL|EMPTY|DISPOSED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long v) { this.organizationId = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public String getColor() { return color; }
    public void setColor(String v) { this.color = v; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String v) { this.colorHex = v; }
    public Integer getMassInitialG() { return massInitialG; }
    public void setMassInitialG(Integer v) { this.massInitialG = v; }
    public Integer getMassRemainingG() { return massRemainingG; }
    public void setMassRemainingG(Integer v) { this.massRemainingG = v; }
    public String getSerialBarcode() { return serialBarcode; }
    public void setSerialBarcode(String v) { this.serialBarcode = v; }
    public String getVendor() { return vendor; }
    public void setVendor(String v) { this.vendor = v; }
    public String getLotCode() { return lotCode; }
    public void setLotCode(String v) { this.lotCode = v; }
    public LocalDate getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(LocalDate v) { this.purchasedAt = v; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate v) { this.expiresAt = v; }
    public Long getAssignedPrinterId() { return assignedPrinterId; }
    public void setAssignedPrinterId(Long v) { this.assignedPrinterId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
