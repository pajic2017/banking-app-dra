package com.bank.transactionservice.service;

import com.bank.transactionservice.client.PostingClient;
import com.bank.transactionservice.repository.TransactionRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class TransactionProcessor {
    private final TransactionRepository repository;
    private final PostingClient postings;
    private final Clock clock;
    public TransactionProcessor(TransactionRepository repository, PostingClient postings, Clock clock) {
        this.repository = repository; this.postings = postings; this.clock = clock;
    }

    @Transactional
    public boolean processNext() {
        var transaction = repository.lockNext(clock.instant());
        if (transaction.isEmpty()) return false;
        var row = transaction.get();
        com.bank.contracts.posting.v1.PostingResultV1 result;
        try { result = postings.post(row.command()); }
        catch (RestClientException | IllegalStateException unavailable) {
            // An HTTP failure can follow a successful commit. Never infer rejection or compensate here.
            repository.retry(row, clock.instant().truncatedTo(ChronoUnit.MICROS), "POSTING_UNCONFIRMED");
            return true;
        }
        // If this local update/commit fails, the durable PENDING command is retried with the same operation ID.
        repository.complete(row.id(), result, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return true;
    }
}
