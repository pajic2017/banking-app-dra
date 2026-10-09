package com.bank.customerservice.repository;

import com.bank.customerservice.domain.Customer;
import com.bank.customerservice.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
}
