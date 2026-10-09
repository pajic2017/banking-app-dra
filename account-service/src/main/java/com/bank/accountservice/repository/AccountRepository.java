package com.bank.accountservice.repository;
import com.bank.accountservice.domain.Account;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountRepository extends JpaRepository<Account,Long> {
    Page<Account> findByCustomerId(Long customerId, Pageable pageable);
}
