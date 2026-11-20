package com.llms.loan_application.credit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CreditLineRepository extends JpaRepository<CreditLine, Long> {

    @Transactional
    @Modifying
    @Query("update CreditLine c set c.remainingLimit = c.remainingLimit - :amount " +
            "where c.id = :id and c.remainingLimit >= :amount")
    int deduct(@Param("id") Long id, @Param("amount") int amount);
}