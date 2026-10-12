package com.bank.transactionservice.service;

import com.bank.contracts.posting.v1.*;
import com.bank.transactionservice.api.*;
import com.bank.transactionservice.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final Clock clock;
    public TransactionService(TransactionRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Transactional
    public TransactionView submitAuthenticated(String key,PostingType type,Long from,Long to,BigDecimal amount,String currency,String description) {
        if(repository.legacyKeyExists(key))throw new TransactionFailure(HttpStatus.CONFLICT,"Legacy idempotency key has no verified caller binding; review the existing transaction before issuing a new key.");
        var access=new com.bank.security.BankAccess();var scoped=access.scopedKey(key);
        var result=submit(scoped,type,from,to,amount,currency,description);
        if(!repository.subjectMatches(scoped,access.subject()))throw new TransactionFailure(HttpStatus.CONFLICT,"Idempotency key has no matching verified caller binding.");
        return result;
    }

    @Transactional
    public TransactionView submit(String key, PostingType type, Long from, Long to, BigDecimal amount, String currency, String description) {
        var command = new PostingCommandV1(UUID.randomUUID(), type, from, to, amount, currency);
        description = description == null ? "" : description.trim();
        var hash = PostingCommandV1.hash(command.instructionHash() + "|" + description);
        repository.insert(command, key, description, hash, clock.instant().truncatedTo(ChronoUnit.MICROS));
        // PostgreSQL's unique key also serializes simultaneous submissions; no check-then-insert race.
        if (!repository.requestHash(key).equals(hash)) {
            throw new TransactionFailure(HttpStatus.CONFLICT, "Idempotency key was already used for a different request.");
        }
        return repository.byKey(key);
    }

    @Transactional(readOnly=true)
    public TransactionView get(UUID id) {
        return repository.get(id).orElseThrow(() -> new TransactionFailure(HttpStatus.NOT_FOUND, "Transaction was not found."));
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TransactionPage page(Long accountId, TransactionStatus status, PostingType type, int page, int size) {
        return repository.page(accountId, status, type, page, size);
    }
}
