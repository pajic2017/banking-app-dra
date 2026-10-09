package com.bank.customerservice.service;

import com.bank.customerservice.api.*;
import com.bank.customerservice.domain.*;
import com.bank.customerservice.repository.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository repository;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        var customer = new Customer(request.firstName(), request.lastName(), request.email(),
                request.phone(), now());
        // Flush here: the unique database constraint also protects concurrent creates.
        return CustomerResponse.from(repository.saveAndFlush(customer));
    }

    public CustomerResponse get(long id) { return CustomerResponse.from(find(id)); }

    public CustomerPage list(int page, int size, CustomerStatus status) {
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        var result = status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        return CustomerPage.from(result.map(CustomerResponse::from));
    }

    @Transactional
    public CustomerResponse update(long id, CustomerRequest request) {
        var customer = find(id);
        customer.update(request.firstName(), request.lastName(), request.email(), request.phone(), now());
        repository.flush(); // Detect uniqueness/version conflicts before returning success.
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void deactivate(long id) {
        find(id).deactivate(now()); // Dirty checking persists status; no DELETE statement.
        repository.flush();
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private Customer find(long id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
