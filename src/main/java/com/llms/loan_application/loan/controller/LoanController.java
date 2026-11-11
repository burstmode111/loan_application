package com.llms.loan_application.loan;

import com.llms.loan_application.loan.dto.LoanResponse;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/loans")
public class LoanController {

    record CreateLoanRequest(String applicantName, BigDecimal amount) {}

    private final LoanService service;

    public LoanController(LoanService service) { this.service = service; }

    // get single loan data
    @GetMapping("/{id}")
    public LoanResponse getLoan(@PathVariable("id") Long id) {
        return service.getLoan(id);
    }

    // create loan data
    @PostMapping
    public Long create(@RequestBody CreateLoanRequest req) {
        return service.createLoan(req.applicantName(), req.amount());
    }
}
