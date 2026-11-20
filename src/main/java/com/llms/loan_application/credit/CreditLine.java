package com.llms.loan_application.credit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "credit_line")
public class CreditLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "total_limit")
    private int totalLimit;

    @Column(name = "remaining_limit")
    private int remainingLimit;

    private OffsetDateTime createdAt;

    protected CreditLine() {
    }

    public CreditLine(String name, int totalLimit) {
        this.name = name;
        this.totalLimit = totalLimit;
        this.remainingLimit = totalLimit;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getTotalLimit() { return totalLimit; }
    public int getRemainingLimit() { return remainingLimit; }
    public void setRemainingLimit(int remainingLimit) { this.remainingLimit = remainingLimit; }
}