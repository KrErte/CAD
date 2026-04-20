package ee.krerte.cad.gallery;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "print_orders")
public class PrintOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "design_id")
    private Long designId;

    @Column(nullable = false)
    private String material = "PLA";

    @Column(name = "infill_pct", nullable = false)
    private int infillPct = 20;

    @Column(nullable = false)
    private int quantity = 1;

    private String color = "must";

    @Column(name = "shipping_name")
    private String shippingName;
    @Column(name = "shipping_address")
    private String shippingAddress;
    @Column(name = "shipping_city")
    private String shippingCity;
    @Column(name = "shipping_zip")
    private String shippingZip;
    @Column(name = "shipping_country")
    private String shippingCountry = "EE";

    @Column(name = "price_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceEur;

    @Column(nullable = false)
    private String status = "pending";

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long u) { this.userId = u; }
    public Long getDesignId() { return designId; }
    public void setDesignId(Long d) { this.designId = d; }
    public String getMaterial() { return material; }
    public void setMaterial(String m) { this.material = m; }
    public int getInfillPct() { return infillPct; }
    public void setInfillPct(int i) { this.infillPct = i; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { this.quantity = q; }
    public String getColor() { return color; }
    public void setColor(String c) { this.color = c; }
    public String getShippingName() { return shippingName; }
    public void setShippingName(String s) { this.shippingName = s; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String s) { this.shippingAddress = s; }
    public String getShippingCity() { return shippingCity; }
    public void setShippingCity(String s) { this.shippingCity = s; }
    public String getShippingZip() { return shippingZip; }
    public void setShippingZip(String s) { this.shippingZip = s; }
    public String getShippingCountry() { return shippingCountry; }
    public void setShippingCountry(String s) { this.shippingCountry = s; }
    public BigDecimal getPriceEur() { return priceEur; }
    public void setPriceEur(BigDecimal p) { this.priceEur = p; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant u) { this.updatedAt = u; }
}
