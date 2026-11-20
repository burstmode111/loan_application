package com.llms.loan_application.credit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GrabRecordRepository extends JpaRepository<GrabRecord, Long> {
    long countByCreditLineId(Long creditLineId);
}