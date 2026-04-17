package ee.krerte.cad.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "usage_monthly",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year_month"}))
public class Usage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Format: YYYY-MM */
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(nullable = false)
    private int stlCount = 0;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getYearMonth() { return yearMonth; }
    public void setYearMonth(String ym) { this.yearMonth = ym; }
    public int getStlCount() { return stlCount; }
    public void setStlCount(int c) { this.stlCount = c; }
    public void increment() { this.stlCount++; }
}
