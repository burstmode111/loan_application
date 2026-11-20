package com.llms.loan_application.credit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditLineService {

    private final CreditLineRepository lines;
    private final GrabRecordRepository records;

    public CreditLineService(CreditLineRepository lines, GrabRecordRepository records) {
        this.lines = lines;
        this.records = records;
    }

    @Transactional
    public Long create(String name, int totalLimit) {
        return lines.save(new CreditLine(name, totalLimit)).getId();
    }

    @Transactional
    public void grab(Long id, int amount) {
        CreditLine line = lines.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "credit line not found"));

        if (line.getRemainingLimit() < amount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no limit");
        }

        line.setRemainingLimit(line.getRemainingLimit() - amount);
        records.save(new GrabRecord(id, amount));
    }

    @Transactional
    public void grabAtomic(Long id, int amount) {
        int updated = lines.deduct(id, amount);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no limit");
        }
        records.save(new GrabRecord(id, amount));
    }
}