package com.bank.accountservice.service;

import com.bank.accountservice.domain.Account;
import com.bank.accountservice.domain.AccountStatus;
import com.bank.accountservice.repository.AccountRepository;
import com.bank.contracts.posting.v1.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostingService {
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostingService(AccountRepository accounts, JdbcTemplate jdbc, Clock clock) {
        this.accounts = accounts; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PostingResultV1 post(PostingCommandV1 command) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var id = command.operationId().toString();
        // This unique-key insert serializes duplicates before either request touches balances.
        jdbc.update("""
                INSERT INTO posting_receipts(operation_id,instruction_hash,posting_type,from_account_id,to_account_id,amount,currency,status,recorded_at)
                VALUES (?,?,?,?,?,?,?,'APPLYING',?) ON DUPLICATE KEY UPDATE operation_id=operation_id
                """, id, command.instructionHash(), command.type().name(), command.fromAccountId(), command.toAccountId(),
                command.amount(), command.currency(), Timestamp.from(now));
        var receipt = jdbc.queryForMap("SELECT * FROM posting_receipts WHERE operation_id=? FOR UPDATE", id);
        if (!command.instructionHash().equals(receipt.get("instruction_hash"))) {
            throw new AccountFailure(HttpStatus.CONFLICT, "Operation ID was already used for a different instruction.");
        }
        var status = receipt.get("status").toString();
        if (!status.equals("APPLYING")) {
            return new PostingResultV1(command.operationId(), PostingStatus.valueOf(status),
                    (String) receipt.get("failure_code"), jdbc.queryForObject("SELECT recorded_at FROM posting_receipts WHERE operation_id=?", (row,n) -> row.getTimestamp(1).toInstant(), id), command.instructionHash());
        }
        var locked = new HashMap<Long,Account>();
        // Every posting locks in ascending ID order, including opposite-direction transfers.
        java.util.stream.Stream.of(command.fromAccountId(), command.toAccountId())
                .filter(Objects::nonNull).sorted().forEach(accountId ->
                    accounts.lockById(accountId).ifPresent(a -> locked.put(accountId,a)));
        String failure = validate(command, locked);
        if (failure != null) return finish(command, PostingStatus.REJECTED, failure, now);
        Account source = locked.get(command.fromAccountId());
        Account target = locked.get(command.toAccountId());
        if (source != null) source.applyPosting(command.amount().negate(), now);
        if (target != null) target.applyPosting(command.amount(), now);
        accounts.flush();
        // Deposits/withdrawals have an external-clearing counterentry; transfers have two customer entries.
        entry(command, 1, source, command.amount().negate(), now);
        entry(command, 2, target, command.amount(), now);
        return finish(command, PostingStatus.COMPLETED, null, now);
    }

    private String validate(PostingCommandV1 command, Map<Long,Account> locked) {
        for (var id : new Long[]{command.fromAccountId(), command.toAccountId()}) {
            if (id == null) continue;
            var account = locked.get(id);
            if (account == null) return "ACCOUNT_NOT_FOUND";
            if (account.getStatus() != AccountStatus.ACTIVE) return "ACCOUNT_NOT_ACTIVE";
            if (!account.getCurrency().equals(command.currency())) return "CURRENCY_MISMATCH";
        }
        if (command.fromAccountId() != null && locked.get(command.fromAccountId()).getBalance().compareTo(command.amount()) < 0) {
            return "INSUFFICIENT_FUNDS";
        }
        if (command.toAccountId() != null && locked.get(command.toAccountId()).getBalance().add(command.amount()).compareTo(PostingCommandV1.MAX_BALANCE) > 0) {
            return "BALANCE_LIMIT";
        }
        return null;
    }

    private void entry(PostingCommandV1 command, int line, Account account, BigDecimal delta, Instant now) {
        jdbc.update("""
                INSERT INTO ledger_entries(operation_id,line_no,account_id,ledger_role,currency,amount,created_at)
                VALUES (?,?,?,?,?,?,?)
                """, command.operationId().toString(), line, account == null ? null : account.getId(),
                account == null ? "CLEARING" : "CUSTOMER", command.currency(), delta, Timestamp.from(now));
    }

    private PostingResultV1 finish(PostingCommandV1 command, PostingStatus status, String failure, Instant now) {
        jdbc.update("UPDATE posting_receipts SET status=?,failure_code=?,recorded_at=? WHERE operation_id=?",
                status.name(), failure, Timestamp.from(now), command.operationId().toString());
        return new PostingResultV1(command.operationId(), status, failure, now, command.instructionHash());
    }
}
