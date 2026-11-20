package com.llms.loan_application.credit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/credit-lines")
public class CreditLineController {

    public record CreateBody(String name, int totalLimit) {}
    public record GrabBody(int amount) {}
    public record CreditLineView(Long id, String name, int totalLimit, int remainingLimit) {}

    private final CreditLineService service;
    private final CreditLineRepository lines;

    public CreditLineController(CreditLineService service, CreditLineRepository lines) {
        this.service = service;
        this.lines = lines;
    }

    @PostMapping
    public Long create(@RequestBody CreateBody body) {
        return service.create(body.name(), body.totalLimit());
    }

    @PostMapping("/{id}/grab")
    public String grab(@PathVariable Long id, @RequestBody GrabBody body) {
        service.grab(id, body.amount());
        return "ok";
    }

    @GetMapping("/{id}")
    public CreditLineView get(@PathVariable Long id) {
        CreditLine line = lines.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "credit line not found"));
        return new CreditLineView(line.getId(), line.getName(), line.getTotalLimit(), line.getRemainingLimit());
    }
}