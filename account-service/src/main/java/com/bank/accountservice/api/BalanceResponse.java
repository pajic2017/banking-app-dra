package com.bank.accountservice.api;
import java.math.BigDecimal;
public record BalanceResponse(Long accountId, String currency, BigDecimal balance) { }
