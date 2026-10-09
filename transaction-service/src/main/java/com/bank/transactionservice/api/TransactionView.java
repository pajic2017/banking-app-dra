package com.bank.transactionservice.api;
import com.bank.contracts.posting.v1.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record TransactionView(UUID id, String transactionReference, PostingType type,
        Long fromAccountId, Long toAccountId, BigDecimal amount, String currency, String description,
        TransactionStatus status, String failureCode, int attempts, String lastRetryCode,
        Instant createdAt, Instant updatedAt) {
    public PostingCommandV1 command() {
        return new PostingCommandV1(id, type, fromAccountId, toAccountId, amount, currency);
    }
}
