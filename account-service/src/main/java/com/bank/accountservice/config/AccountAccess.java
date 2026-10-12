package com.bank.accountservice.config;
import com.bank.accountservice.repository.AccountRepository;
import com.bank.security.BankAccess;
import org.springframework.stereotype.Component;
@Component("accountAccess")
public class AccountAccess {
    private final AccountRepository accounts;
    private final BankAccess access;
    public AccountAccess(AccountRepository accounts,BankAccess access) { this.accounts=accounts;this.access=access; }
    public boolean read(long id) { return access.staff() || accounts.findById(id).map(a -> access.customer(a.getCustomerId())).orElse(false); }
}
