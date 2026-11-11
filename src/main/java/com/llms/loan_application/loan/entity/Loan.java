package com.llms.loan_application.loan;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "loan_application")
public class Loan {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false) private String applicantName;
	@Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
	@Column(nullable = false) private String status;
	@Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

	protected Loan() {}

	public Loan(String applicantName, BigDecimal amount) {
		this.applicantName = applicantName;
		this.amount = amount;
		this.status = "SUBMITTED";
	}
	public Long getId() { return id; }
	public String getApplicantName() { return applicantName; }
	public BigDecimal getAmount() { return amount; }
	public String getStatus() { return status; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
