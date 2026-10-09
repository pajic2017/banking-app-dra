package com.bank.accountservice.service;
import com.bank.accountservice.api.*;
import com.bank.accountservice.client.CustomerClient;
import com.bank.accountservice.domain.*;
import com.bank.accountservice.repository.AccountRepository;
import java.util.Currency;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountService {
    private final AccountRepository repository;
    private final CustomerClient customers;
    private final AccountRegistration registration;
    private final Clock clock;
    public AccountService(AccountRepository repository, CustomerClient customers, AccountRegistration registration, Clock clock) {
        this.repository=repository; this.customers=customers; this.registration=registration; this.clock=clock;
    }
    public AccountResponse create(CreateAccountRequest request) {
        try {
            if (Currency.getInstance(request.currency()).getDefaultFractionDigits()<0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new AccountFailure(HttpStatus.BAD_REQUEST,"Currency must be a supported ISO 4217 monetary currency."); }
        customers.requireActive(request.customerId());
        return registration.create(request);
    }
    @Transactional(readOnly=true) public AccountResponse get(long id) { return AccountResponse.from(find(id)); }
    @Transactional(readOnly=true) public AccountPage byCustomer(long customerId, int page, int size) {
        return AccountPage.from(repository.findByCustomerId(customerId,PageRequest.of(page,size,Sort.by("id"))).map(AccountResponse::from));
    }
    @Transactional(readOnly=true) public BalanceResponse balance(long id) {
        var account=get(id); return new BalanceResponse(account.id(),account.currency(),account.balance());
    }
    @Transactional public AccountResponse status(long id, AccountStatus target) {
        var account=find(id); account.changeStatus(target,clock.instant().truncatedTo(ChronoUnit.MICROS)); repository.flush();
        return AccountResponse.from(account);
    }
    private Account find(long id) { return repository.findById(id).orElseThrow(()->new AccountFailure(HttpStatus.NOT_FOUND,"Account "+id+" was not found.")); }
}
