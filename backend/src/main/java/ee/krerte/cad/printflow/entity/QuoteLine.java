package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quote_lines")
public class QuoteLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo = 1;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "stl_bytes")
    @Lob
    private byte[] stlBytes;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "infill_pct", nullable = false)
    private Integer infillPct = 20;

    @Column(name = "layer_height_mm", nullable = false, precision = 4, scale = 2)
    private BigDecimal layerHeightMm = new BigDecimal("0.20");

    private String color;

    @Column(name = "unit_price_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceEur = BigDecimal.ZERO;

    @Column(name = "total_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEur = BigDecimal.ZERO;

    @Column(name = "slicer_result", columnDefinition = "text")
    private String slicerResult;   // JSON-encoded slicer response

    @Column(name = "dfm_report_id")
    private Long dfmReportId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long v) { this.quoteId = v; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer v) { this.lineNo = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { this.fileName = v; }
    public byte[] getStlBytes() { return stlBytes; }
    public void setStlBytes(byte[] v) { this.stlBytes = v; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { this.quantity = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public Integer getInfillPct() { return infillPct; }
    public void setInfillPct(Integer v) { this.infillPct = v; }
    public BigDecimal getLayerHeightMm() { return layerHeightMm; }
    public void setLayerHeightMm(BigDecimal v) { this.layerHeightMm = v; }
    public String getColor() { return color; }
    public void setColor(String v) { this.color = v; }
    public BigDecimal getUnitPriceEur() { return unitPriceEur; }
    public void setUnitPriceEur(BigDecimal v) { this.unitPriceEur = v; }
    public BigDecimal getTotalEur() { return totalEur; }
    public void setTotalEur(BigDecimal v) { this.totalEur = v; }
    public String getSlicerResult() { return slicerResult; }
    public void setSlicerResult(String v) { this.slicerResult = v; }
    public Long getDfmReportId() { return dfmReportId; }
    public void setDfmReportId(Long v) { this.dfmReportId = v; }
    public Instant getCreatedAt() { return createdAt; }
}
