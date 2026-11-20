package com.llms.loan_application.credit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "grab_record")
public class GrabRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_line_id")
    private Long creditLineId;

    private int amount;

    private OffsetDateTime createdAt;

    protected GrabRecord() {
    }

    public GrabRecord(Long creditLineId, int amount) {
        this.creditLineId = creditLineId;
        this.amount = amount;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getCreditLineId() { return creditLineId; }
    public int getAmount() { return amount; }
}