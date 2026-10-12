package com.bank.accountservice.service;
import com.bank.accountservice.api.*;
import com.bank.accountservice.domain.Account;
import com.bank.accountservice.repository.AccountRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Separate proxy establishes a write transaction only after remote customer verification. */
@Service
public class AccountRegistration {
    private final AccountRepository repository;
    private final Clock clock;
    private final com.bank.accountservice.events.EventOutbox events;
    public AccountRegistration(AccountRepository repository, Clock clock, com.bank.accountservice.events.EventOutbox events) { this.repository=repository; this.clock=clock;this.events=events; }
    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        // Internal reference, not an IBAN. Uniqueness is ultimately enforced by MySQL.
        var number="BA"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
        var account=new Account(request.customerId(),number,request.accountType(),request.currency(),clock.instant().truncatedTo(ChronoUnit.MICROS));
        var saved=repository.saveAndFlush(account);
        events.add(com.bank.contracts.events.v1.EventType.ACCOUNT_CREATED,String.valueOf(saved.getId()),
                com.bank.accountservice.events.EventOutbox.currentCorrelation(),java.util.Map.of(
                    "accountId",String.valueOf(saved.getId()),"customerId",String.valueOf(saved.getCustomerId()),
                    "accountType",saved.getAccountType().name(),"currency",saved.getCurrency()));
        return AccountResponse.from(saved);
    }
}
