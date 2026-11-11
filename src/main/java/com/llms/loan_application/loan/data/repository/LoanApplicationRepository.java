package com.llms.loan_application.loan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationRepository extends JpaRepository<Loan, Long> {}