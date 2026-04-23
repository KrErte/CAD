package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "print_jobs")
public class PrintJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "quote_id")
    private Long quoteId;

    @Column(name = "quote_line_id")
    private Long quoteLineId;

    @Column(name = "build_plate_id")
    private Long buildPlateId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "spool_id")
    private Long spoolId;

    @Column(name = "printer_id")
    private Long printerId;

    @Column(nullable = false)
    private Integer priority = 50;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "gcode_ref")
    private String gcodeRef;

    @Column(name = "estimated_time_sec")
    private Integer estimatedTimeSec;

    @Column(name = "estimated_filament_g", precision = 8, scale = 2)
    private BigDecimal estimatedFilamentG;

    @Column(nullable = false, length = 16)
    private String status = "QUEUED"; // QUEUED|ASSIGNED|PRINTING|PAUSED|DONE|FAILED|CANCELLED

    @Column(name = "progress_pct", nullable = false)
    private Integer progressPct = 0;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(nullable = false)
    private Integer retries = 0;

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long v) {
        this.organizationId = v;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public void setQuoteId(Long v) {
        this.quoteId = v;
    }

    public Long getQuoteLineId() {
        return quoteLineId;
    }

    public void setQuoteLineId(Long v) {
        this.quoteLineId = v;
    }

    public Long getBuildPlateId() {
        return buildPlateId;
    }

    public void setBuildPlateId(Long v) {
        this.buildPlateId = v;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long v) {
        this.materialId = v;
    }

    public Long getSpoolId() {
        return spoolId;
    }

    public void setSpoolId(Long v) {
        this.spoolId = v;
    }

    public Long getPrinterId() {
        return printerId;
    }

    public void setPrinterId(Long v) {
        this.printerId = v;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer v) {
        this.priority = v;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String v) {
        this.jobName = v;
    }

    public String getGcodeRef() {
        return gcodeRef;
    }

    public void setGcodeRef(String v) {
        this.gcodeRef = v;
    }

    public Integer getEstimatedTimeSec() {
        return estimatedTimeSec;
    }

    public void setEstimatedTimeSec(Integer v) {
        this.estimatedTimeSec = v;
    }

    public BigDecimal getEstimatedFilamentG() {
        return estimatedFilamentG;
    }

    public void setEstimatedFilamentG(BigDecimal v) {
        this.estimatedFilamentG = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public Integer getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(Integer v) {
        this.progressPct = v;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant v) {
        this.startedAt = v;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant v) {
        this.finishedAt = v;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String v) {
        this.failureReason = v;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer v) {
        this.retries = v;
    }
}
