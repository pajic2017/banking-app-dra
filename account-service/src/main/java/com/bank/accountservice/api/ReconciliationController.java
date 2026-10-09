package com.bank.accountservice.api;
import com.bank.accountservice.service.LedgerReconciliation;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReconciliationController {
    private final LedgerReconciliation ledger;
    public ReconciliationController(LedgerReconciliation ledger) { this.ledger = ledger; }
    @GetMapping("/internal/v1/ledger/reconciliation")
    public LedgerReconciliation.Report check(@RequestParam String currency) { return ledger.check(currency); }
}
