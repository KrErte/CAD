package ee.krerte.cad.printflow.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "printer_events")
public class PrinterEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_id", nullable = false)
    private Long printerId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;  // HEARTBEAT|JOB_START|JOB_DONE|JOB_FAIL|ERROR|TEMP|COMMAND

    /** JSON text payload (jsonb column). Service layer serializes to JSON. */
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public Long getId() { return id; }
    public Long getPrinterId() { return printerId; }
    public void setPrinterId(Long v) { this.printerId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant v) { this.occurredAt = v; }
}
