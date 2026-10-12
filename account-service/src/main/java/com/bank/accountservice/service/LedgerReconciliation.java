package com.bank.accountservice.service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class LedgerReconciliation {
    private final JdbcTemplate jdbc;
    public LedgerReconciliation(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Report(String currency, BigDecimal customerBalanceTotal, BigDecimal customerLedgerTotal,
            BigDecimal clearingLedgerTotal, long accountMismatches, long invalidPostings, boolean balanced) { }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Report check(String code) {
        code = code.trim().toUpperCase(Locale.ROOT);
        try { if (Currency.getInstance(code).getDefaultFractionDigits() < 0) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException invalid) { throw new AccountFailure(HttpStatus.BAD_REQUEST,"Unsupported monetary currency."); }
        var balances = jdbc.queryForObject("SELECT COALESCE(SUM(balance),0) FROM accounts WHERE currency=?",BigDecimal.class,code);
        var customer = total(code,"CUSTOMER");
        var clearing = total(code,"CLEARING");
        long mismatches = jdbc.queryForObject("""
            SELECT COUNT(*) FROM accounts a LEFT JOIN (
                SELECT account_id,SUM(amount) AS balance FROM ledger_entries WHERE ledger_role='CUSTOMER' AND currency=? GROUP BY account_id
            ) l ON a.id=l.account_id WHERE a.currency=? AND a.balance<>COALESCE(l.balance,0)
            """,Long.class,code,code);
        long invalid = jdbc.queryForObject("""
            SELECT COUNT(*) FROM (
                SELECT p.operation_id,p.status,p.posting_type,p.from_account_id,p.to_account_id,p.amount,p.currency,
                    COUNT(l.line_no) AS entry_count,COALESCE(SUM(l.amount),0) AS net,
                    SUM(CASE WHEN l.line_no=1 AND l.amount=-p.amount AND l.currency=p.currency AND
                        ((p.from_account_id IS NULL AND l.account_id IS NULL AND l.ledger_role='CLEARING') OR
                         (l.account_id=p.from_account_id AND l.ledger_role='CUSTOMER')) THEN 1 ELSE 0 END) AS debit,
                    SUM(CASE WHEN l.line_no=2 AND l.amount=p.amount AND l.currency=p.currency AND
                        ((p.to_account_id IS NULL AND l.account_id IS NULL AND l.ledger_role='CLEARING') OR
                         (l.account_id=p.to_account_id AND l.ledger_role='CUSTOMER')) THEN 1 ELSE 0 END) AS credit
                FROM posting_receipts p LEFT JOIN ledger_entries l ON l.operation_id=p.operation_id
                WHERE p.currency=? GROUP BY p.operation_id,p.status,p.posting_type,p.from_account_id,p.to_account_id,p.amount,p.currency
                HAVING p.status='APPLYING' OR (p.status='COMPLETED' AND (entry_count<>2 OR net<>0 OR debit<>1 OR credit<>1))
                    OR (p.status='REJECTED' AND entry_count<>0)
            ) broken
            """,Long.class,code);
        return new Report(code,balances,customer,clearing,mismatches,invalid,
                mismatches == 0 && invalid == 0 && customer.add(clearing).signum() == 0 && balances.compareTo(customer) == 0);
    }
    private BigDecimal total(String code,String role) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE currency=? AND ledger_role=?",BigDecimal.class,code,role);
    }
}
