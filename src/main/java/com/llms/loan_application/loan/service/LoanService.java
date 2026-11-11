package com.llms.loan_application.loan;

import com.llms.loan_application.loan.dto.LoanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class LoanService {

    private final LoanApplicationRepository loans;
    private final OutboxEventRepository outbox;

    public LoanService(LoanApplicationRepository loans, OutboxEventRepository outbox) {
        this.loans = loans;
        this.outbox = outbox;
    }

    @Transactional
    public Long createLoan(String name, BigDecimal amount) {
        Loan saved = loans.save(new Loan(name, amount));
        outbox.save(new OutboxEvent(saved.getId(), "LOAN_SUBMITTED",
                "{\"loanId\":" + saved.getId() + "}"));
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoan(Long id) {
        Loan loan = loans.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "loan not found: " + id));
        return new LoanResponse(loan.getId(), loan.getApplicantName(),
                loan.getAmount(), loan.getStatus(), loan.getCreatedAt());
    }
}
