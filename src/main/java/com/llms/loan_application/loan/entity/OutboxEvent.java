package com.llms.loan_application.loan;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected OutboxEvent() {}

    public OutboxEvent(Long aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }
    public Long getId() { return id; }
    public Long getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
}