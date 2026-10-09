package com.bank.accountservice.api;
import com.bank.accountservice.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;
public record AccountStatusRequest(@NotNull AccountStatus status) { }
