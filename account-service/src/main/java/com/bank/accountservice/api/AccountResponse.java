package com.bank.accountservice.api;
import com.bank.accountservice.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
public record AccountResponse(Long id, Long customerId, String accountNumber, AccountType accountType,
                              String currency, AccountStatus status, BigDecimal balance, Instant createdAt, Instant updatedAt) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(),a.getCustomerId(),a.getAccountNumber(),a.getAccountType(),a.getCurrency(),
                a.getStatus(),a.getBalance().setScale(Currency.getInstance(a.getCurrency()).getDefaultFractionDigits()),a.getCreatedAt(),a.getUpdatedAt());
    }
}
