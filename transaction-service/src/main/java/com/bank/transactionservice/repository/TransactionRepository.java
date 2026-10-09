package com.bank.transactionservice.repository;

import com.bank.contracts.posting.v1.*;
import com.bank.transactionservice.api.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {
    private final JdbcTemplate jdbc;
    public TransactionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(PostingCommandV1 command, String key, String description, String hash, Instant now) {
        jdbc.update("""
            INSERT INTO transactions(id,transaction_reference,idempotency_key,request_hash,transaction_type,
                from_account_id,to_account_id,amount,currency,description,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (idempotency_key) DO NOTHING
            """, command.operationId(), "TX-" + command.operationId(), key, hash, command.type().name(),
            command.fromAccountId(), command.toAccountId(), command.amount(), command.currency(), description,
            Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    public String requestHash(String key) { return jdbc.queryForObject("SELECT request_hash FROM transactions WHERE idempotency_key=?", String.class, key); }
    public TransactionView byKey(String key) { return jdbc.queryForObject("SELECT * FROM transactions WHERE idempotency_key=?", this::map, key); }
    public Optional<TransactionView> get(UUID id) { return jdbc.query("SELECT * FROM transactions WHERE id=?", this::map, id).stream().findFirst(); }

    /** Call only inside a transaction: row locks are retained until the processor commits. */
    public Optional<TransactionView> lockNext(Instant now) {
        return jdbc.query("""
            SELECT * FROM transactions WHERE status='PENDING' AND next_attempt_at<=?
            ORDER BY next_attempt_at,created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, this::map, Timestamp.from(now)).stream().findFirst();
    }

    public void complete(UUID id, PostingResultV1 result, Instant now) {
        jdbc.update("UPDATE transactions SET status=?,failure_code=?,attempts=attempts+1,last_retry_code=NULL,updated_at=? WHERE id=?",
                result.status().name(), result.failureCode(), Timestamp.from(now), id);
    }

    public void retry(TransactionView transaction, Instant now, String code) {
        long seconds = Math.min(60, 1L << Math.min(transaction.attempts(), 6));
        jdbc.update("UPDATE transactions SET attempts=attempts+1,last_retry_code=?,next_attempt_at=?,updated_at=? WHERE id=?",
                code, Timestamp.from(now.plusSeconds(seconds)), Timestamp.from(now), transaction.id());
    }

    public TransactionPage page(Long accountId, TransactionStatus status, PostingType type, int page, int size) {
        String where = " WHERE 1=1";
        var parameters = new ArrayList<Object>();
        if (accountId != null) { where += " AND (from_account_id=? OR to_account_id=?)"; parameters.add(accountId); parameters.add(accountId); }
        if (status != null) { where += " AND status=?"; parameters.add(status.name()); }
        if (type != null) { where += " AND transaction_type=?"; parameters.add(type.name()); }
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM transactions" + where, Long.class, parameters.toArray());
        parameters.add(size); parameters.add((long) page * size);
        var items = jdbc.query("SELECT * FROM transactions" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", this::map, parameters.toArray());
        return new TransactionPage(items, page, size, count, (int)((count + size - 1) / size));
    }

    private TransactionView map(ResultSet row, int number) throws SQLException {
        String currency = row.getString("currency");
        return new TransactionView(row.getObject("id",UUID.class), row.getString("transaction_reference"),
                PostingType.valueOf(row.getString("transaction_type")), (Long)row.getObject("from_account_id"),
                (Long)row.getObject("to_account_id"), row.getBigDecimal("amount").setScale(java.util.Currency.getInstance(currency).getDefaultFractionDigits()),
                currency, row.getString("description"), TransactionStatus.valueOf(row.getString("status")),
                row.getString("failure_code"), row.getInt("attempts"), row.getString("last_retry_code"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
