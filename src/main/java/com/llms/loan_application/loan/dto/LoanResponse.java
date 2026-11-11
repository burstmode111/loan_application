package com.llms.loan_application.loan.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LoanResponse(
        Long id,
        String applicantName,
        BigDecimal amount,
        String status,
        OffsetDateTime createdAt) {
}
